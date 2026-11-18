package org.jebol.domain.image;

import org.jebol.domain.eval.compression.Octets;
import org.jebol.domain.host.ImagePort.Pixels;

import java.util.Optional;

public final class GifDecoder {

    private static final int EXTENSION = 0x21;
    private static final int IMAGE = 0x2C;
    private static final int TRAILER = 0x3B;
    private static final int GRAPHIC_CONTROL = 0xF9;
    private static final int HEADER_BYTES = 13;
    private static final int TABLE_PRESENT = 0x80;
    private static final int INTERLACED = 0x40;
    private static final int TABLE_SIZE_BITS = 0x07;
    private static final int TRANSPARENCY_GIVEN = 0x01;
    private static final int DISPOSAL_SHIFT = 2;
    private static final int DISPOSAL_BITS = 0x07;
    private static final int RESTORE_TO_BACKGROUND = 2;
    private static final int RESTORE_TO_PREVIOUS = 3;
    private static final int NO_TRANSPARENCY = -1;
    private static final int CHANNELS = 4;
    private static final int OPAQUE = 0xFF;

    private final byte[] encoded;
    private int at;
    private int disposal;
    private int transparentIndex = NO_TRANSPARENCY;

    private GifDecoder(byte[] encoded) {
        this.encoded = encoded;
    }

    public static boolean isGif(byte[] encoded) {
        return encoded.length >= 6 && encoded[0] == 'G' && encoded[1] == 'I' && encoded[2] == 'F'
                && encoded[3] == '8' && (encoded[4] == '7' || encoded[4] == '9') && encoded[5] == 'a';
    }

    public static Optional<Pixels> decodeFrame(byte[] encoded, int frame) {
        if (!isGif(encoded) || encoded.length < HEADER_BYTES || frame < 1) {
            return Optional.empty();
        }
        try {
            return new GifDecoder(encoded).composeUpTo(frame);
        } catch (Truncated | ArrayIndexOutOfBoundsException unreadable) {
            return Optional.empty();
        }
    }

    private Optional<Pixels> composeUpTo(int frame) {
        int screenWide = readTwoBytes(6);
        int screenHigh = readTwoBytes(8);
        int flags = readByte(10);
        at = HEADER_BYTES;
        int[] globalTable = (flags & TABLE_PRESENT) != 0 ? readTable(flags) : new int[0];
        Canvas canvas = null;
        Frame previous = null;
        int count = 0;
        while (at < encoded.length) {
            int introducer = readByte(at++);
            if (introducer == TRAILER) {
                break;
            }
            if (introducer == EXTENSION) {
                readExtension();
                continue;
            }
            if (introducer != IMAGE) {
                break;
            }
            Frame read = readFrame(globalTable);
            count++;
            if (canvas == null) {
                canvas = new Canvas(screenWide > 0 ? screenWide : read.left + read.wide,
                        screenHigh > 0 ? screenHigh : read.top + read.high);
            } else {
                canvas.dispose(previous);
            }
            canvas.remember(read);
            canvas.paint(read);
            if (count == frame) {
                return Optional.of(canvas.asPixels());
            }
            previous = read;
        }
        return Optional.empty();
    }

    private void readExtension() {
        int label = readByte(at++);
        if (label == GRAPHIC_CONTROL && readByte(at) >= 4) {
            int packed = readByte(at + 1);
            disposal = (packed >> DISPOSAL_SHIFT) & DISPOSAL_BITS;
            transparentIndex = (packed & TRANSPARENCY_GIVEN) != 0 ? readByte(at + 4) : NO_TRANSPARENCY;
        }
        skipSubBlocks();
    }

    private Frame readFrame(int[] globalTable) {
        int left = readTwoBytes(at);
        int top = readTwoBytes(at + 2);
        int wide = readTwoBytes(at + 4);
        int high = readTwoBytes(at + 6);
        int flags = readByte(at + 8);
        at += 9;
        int[] table = (flags & TABLE_PRESENT) != 0 ? readTable(flags) : globalTable;
        int minimumCodeSize = readByte(at++);
        byte[] indices = new LzwDecoder(readSubBlocks(), minimumCodeSize).decode(wide * high);
        Frame frame = new Frame(left, top, wide, high, table, indices, (flags & INTERLACED) != 0,
                disposal, transparentIndex);
        disposal = 0;
        transparentIndex = NO_TRANSPARENCY;
        return frame;
    }

    private int[] readTable(int flags) {
        int entries = 1 << ((flags & TABLE_SIZE_BITS) + 1);
        int[] table = new int[entries];
        for (int entry = 0; entry < entries; entry++) {
            table[entry] = (readByte(at) << 16) | (readByte(at + 1) << 8) | readByte(at + 2);
            at += 3;
        }
        return table;
    }

    private byte[] readSubBlocks() {
        Octets joined = new Octets();
        while (true) {
            int length = readByte(at++);
            if (length == 0) {
                return joined.toArray();
            }
            if (at + length > encoded.length) {
                joined.write(encoded, at, encoded.length - at);
                at = encoded.length;
                return joined.toArray();
            }
            joined.write(encoded, at, length);
            at += length;
        }
    }

    private void skipSubBlocks() {
        while (true) {
            int length = readByte(at++);
            if (length == 0) {
                return;
            }
            at += length;
        }
    }

    private int readByte(int where) {
        if (where >= encoded.length) {
            throw new Truncated();
        }
        return encoded[where] & 0xFF;
    }

    private int readTwoBytes(int where) {
        return readByte(where) | (readByte(where + 1) << 8);
    }

    private static final class Truncated extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private Truncated() {
            super(null, null, false, false);
        }
    }

    private record Frame(int left, int top, int wide, int high, int[] table, byte[] indices,
                         boolean interlaced, int disposal, int transparentIndex) {

        int rowAt(int stored) {
            if (!interlaced) {
                return stored;
            }
            int firstPass = (high + 7) / 8;
            if (stored < firstPass) {
                return stored * 8;
            }
            int secondPass = (high + 3) / 8;
            if (stored < firstPass + secondPass) {
                return 4 + (stored - firstPass) * 8;
            }
            int thirdPass = (high + 1) / 4;
            if (stored < firstPass + secondPass + thirdPass) {
                return 2 + (stored - firstPass - secondPass) * 4;
            }
            return 1 + (stored - firstPass - secondPass - thirdPass) * 2;
        }
    }

    private static final class Canvas {

        private final int wide;
        private final int high;
        private final byte[] rgba;
        private byte[] beforeLastFrame;

        private Canvas(int wide, int high) {
            this.wide = wide;
            this.high = high;
            this.rgba = new byte[wide * high * CHANNELS];
        }

        private void remember(Frame coming) {
            beforeLastFrame = coming.disposal() == RESTORE_TO_PREVIOUS ? rgba.clone() : null;
        }

        private void dispose(Frame shown) {
            if (shown.disposal() == RESTORE_TO_BACKGROUND) {
                for (int down = Math.max(0, shown.top()); down < Math.min(high, shown.top() + shown.high()); down++) {
                    for (int across = Math.max(0, shown.left());
                            across < Math.min(wide, shown.left() + shown.wide()); across++) {
                        int pixel = (down * wide + across) * CHANNELS;
                        rgba[pixel] = 0;
                        rgba[pixel + 1] = 0;
                        rgba[pixel + 2] = 0;
                        rgba[pixel + 3] = 0;
                    }
                }
            } else if (shown.disposal() == RESTORE_TO_PREVIOUS && beforeLastFrame != null) {
                System.arraycopy(beforeLastFrame, 0, rgba, 0, rgba.length);
            }
        }

        private void paint(Frame frame) {
            byte[] indices = frame.indices();
            for (int stored = 0; stored < frame.high(); stored++) {
                int down = frame.top() + frame.rowAt(stored);
                if (down < 0 || down >= high) {
                    continue;
                }
                for (int across = 0; across < frame.wide(); across++) {
                    int x = frame.left() + across;
                    int index = indices[stored * frame.wide() + across] & 0xFF;
                    if (x < 0 || x >= wide || index == frame.transparentIndex()) {
                        continue;
                    }
                    int colour = index < frame.table().length ? frame.table()[index] : 0;
                    int pixel = (down * wide + x) * CHANNELS;
                    rgba[pixel] = (byte) (colour >> 16);
                    rgba[pixel + 1] = (byte) (colour >> 8);
                    rgba[pixel + 2] = (byte) colour;
                    rgba[pixel + 3] = (byte) OPAQUE;
                }
            }
        }

        private Pixels asPixels() {
            return new Pixels(wide, high, rgba.clone());
        }
    }
}
