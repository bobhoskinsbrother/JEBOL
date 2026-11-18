package org.jebol.adapter.host;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class HandBuiltGif {

    static final int NO_TRANSPARENCY = -1;

    private final int screenWide;
    private final int screenHigh;
    private int[] globalPalette;
    private final List<FrameSpec> frames = new ArrayList<>();
    private final List<Written> written = new ArrayList<>();

    record FrameSpec(int left, int top, int wide, int high, byte[] indices, int[] localPalette,
                     boolean interlaced, int disposal, int transparentIndex, int codeSize,
                     boolean deferClearWhenFull) {
    }

    record Written(int widestCode, int clearsAfterTheFirst, int codesWrittenWithTableFull) {
    }

    private HandBuiltGif(int screenWide, int screenHigh) {
        this.screenWide = screenWide;
        this.screenHigh = screenHigh;
    }

    static HandBuiltGif screen(int wide, int high) {
        return new HandBuiltGif(wide, high);
    }

    HandBuiltGif globalPalette(int[] palette) {
        this.globalPalette = palette;
        return this;
    }

    HandBuiltGif frame(FrameSpec frame) {
        frames.add(frame);
        return this;
    }

    static FrameSpec whole(int wide, int high, byte[] indices, int codeSize) {
        return new FrameSpec(0, 0, wide, high, indices, null, false, 0, NO_TRANSPARENCY, codeSize, false);
    }

    static FrameSpec placed(int left, int top, int wide, int high, byte[] indices, int codeSize, int disposal,
            int transparentIndex) {
        return new FrameSpec(left, top, wide, high, indices, null, false, disposal, transparentIndex, codeSize,
                false);
    }

    static FrameSpec withLocalPalette(FrameSpec frame, int[] palette) {
        return new FrameSpec(frame.left(), frame.top(), frame.wide(), frame.high(), frame.indices(), palette,
                frame.interlaced(), frame.disposal(), frame.transparentIndex(), frame.codeSize(),
                frame.deferClearWhenFull());
    }

    static FrameSpec interlaced(FrameSpec frame) {
        return new FrameSpec(frame.left(), frame.top(), frame.wide(), frame.high(), frame.indices(),
                frame.localPalette(), true, frame.disposal(), frame.transparentIndex(), frame.codeSize(),
                frame.deferClearWhenFull());
    }

    static FrameSpec deferringClear(FrameSpec frame) {
        return new FrameSpec(frame.left(), frame.top(), frame.wide(), frame.high(), frame.indices(),
                frame.localPalette(), frame.interlaced(), frame.disposal(), frame.transparentIndex(),
                frame.codeSize(), true);
    }

    List<Written> written() {
        return written;
    }

    byte[] bytes() {
        written.clear();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("GIF89a".getBytes(StandardCharsets.US_ASCII));
        two(out, screenWide);
        two(out, screenHigh);
        if (globalPalette != null) {
            int bits = bitsFor(globalPalette.length);
            out.write(0x80 | ((bits - 1) << 4) | (bits - 1));
        } else {
            out.write(0);
        }
        out.write(0);
        out.write(0);
        if (globalPalette != null) {
            table(out, globalPalette);
        }
        for (FrameSpec frame : frames) {
            out.write(0x21);
            out.write(0xF9);
            out.write(4);
            out.write((frame.disposal() << 2) | (frame.transparentIndex() >= 0 ? 1 : 0));
            two(out, 10);
            out.write(Math.max(0, frame.transparentIndex()));
            out.write(0);
            out.write(0x2C);
            two(out, frame.left());
            two(out, frame.top());
            two(out, frame.wide());
            two(out, frame.high());
            int flags = frame.interlaced() ? 0x40 : 0;
            if (frame.localPalette() != null) {
                flags |= 0x80 | (bitsFor(frame.localPalette().length) - 1);
            }
            out.write(flags);
            if (frame.localPalette() != null) {
                table(out, frame.localPalette());
            }
            out.write(frame.codeSize());
            byte[] stored = frame.interlaced() ? interlace(frame.indices(), frame.wide(), frame.high())
                    : frame.indices();
            Compressor compressor = new Compressor(frame.codeSize(), frame.deferClearWhenFull());
            byte[] compressed = compressor.compress(stored);
            written.add(new Written(compressor.widest, compressor.clears, compressor.codesWhileFull));
            for (int at = 0; at < compressed.length; at += 255) {
                int length = Math.min(255, compressed.length - at);
                out.write(length);
                out.write(compressed, at, length);
            }
            out.write(0);
        }
        out.write(0x3B);
        return out.toByteArray();
    }

    static byte[] interlace(byte[] rows, int wide, int high) {
        byte[] stored = new byte[rows.length];
        int at = 0;
        int[][] passes = {{0, 8}, {4, 8}, {2, 4}, {1, 2}};
        for (int[] pass : passes) {
            for (int row = pass[0]; row < high; row += pass[1]) {
                System.arraycopy(rows, row * wide, stored, at, wide);
                at += wide;
            }
        }
        return stored;
    }

    private static int bitsFor(int entries) {
        int bits = 1;
        while ((1 << bits) < entries) {
            bits++;
        }
        return bits;
    }

    private static void table(ByteArrayOutputStream out, int[] palette) {
        int bits = bitsFor(palette.length);
        for (int entry = 0; entry < (1 << bits); entry++) {
            int colour = entry < palette.length ? palette[entry] : 0;
            out.write(colour >> 16);
            out.write(colour >> 8);
            out.write(colour);
        }
    }

    private static void two(ByteArrayOutputStream out, int value) {
        out.write(value);
        out.write(value >> 8);
    }

    private static final class Compressor {

        private final int codeSize;
        private final boolean deferClear;
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private long bits;
        private int bitCount;
        private int widest;
        private int clears;
        private int codesWhileFull;

        private Compressor(int codeSize, boolean deferClear) {
            this.codeSize = codeSize;
            this.deferClear = deferClear;
        }

        private byte[] compress(byte[] symbols) {
            int clear = 1 << codeSize;
            Map<String, Integer> dictionary = new HashMap<>();
            int width = codeSize + 1;
            int next = clear + 2;
            emit(clear, width);
            String current = "";
            for (byte symbol : symbols) {
                String grown = current + (char) (symbol & 0xFF);
                if (grown.length() == 1 || dictionary.containsKey(grown)) {
                    current = grown;
                    continue;
                }
                emit(codeOf(current, dictionary), width);
                if (next < 4096) {
                    dictionary.put(grown, next);
                    if (next == (1 << width) && width < 12) {
                        width++;
                    }
                    next++;
                } else if (deferClear) {
                    codesWhileFull++;
                } else {
                    emit(clear, width);
                    clears++;
                    dictionary.clear();
                    width = codeSize + 1;
                    next = clear + 2;
                }
                current = String.valueOf((char) (symbol & 0xFF));
            }
            if (!current.isEmpty()) {
                emit(codeOf(current, dictionary), width);
            }
            emit(clear + 1, width);
            if (bitCount > 0) {
                out.write((int) (bits & 0xFF));
            }
            return out.toByteArray();
        }

        private int codeOf(String run, Map<String, Integer> dictionary) {
            return run.length() == 1 ? run.charAt(0) : dictionary.get(run);
        }

        private void emit(int code, int width) {
            widest = Math.max(widest, width);
            bits |= ((long) code) << bitCount;
            bitCount += width;
            while (bitCount >= 8) {
                out.write((int) (bits & 0xFF));
                bits >>>= 8;
                bitCount -= 8;
            }
        }
    }
}
