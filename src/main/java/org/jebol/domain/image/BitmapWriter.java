package org.jebol.domain.image;

import org.jebol.domain.host.ImagePort.Pixels;

public final class BitmapWriter {

    private static final int CHANNELS_PIXEL = 4;
    private static final int FILE_HEADER_BYTES = 14;
    private static final int FIFTH_HEADER_BYTES = 124;
    private static final int EACH_CHANNEL_HAS_MASK = 3;
    private static final int USUAL_COLOUR_SPACE = 0x73524742;

    private BitmapWriter() {
    }

    public static byte[] encode32BitBitmap(Pixels image) {
        int pixelBytes = image.wide() * image.high() * CHANNELS_PIXEL;
        byte[] file = new byte[FILE_HEADER_BYTES + FIFTH_HEADER_BYTES + pixelBytes];
        file[0] = 'B';
        file[1] = 'M';
        addFourBytes(file, 2, file.length);
        addFourBytes(file, 10, FILE_HEADER_BYTES + FIFTH_HEADER_BYTES);

        int header = FILE_HEADER_BYTES;
        addFourBytes(file, header, FIFTH_HEADER_BYTES);
        addFourBytes(file, header + 4, image.wide());
        addFourBytes(file, header + 8, -image.high());
        addTwoBytes(file, header + 12, 1);
        addTwoBytes(file, header + 14, Byte.SIZE * CHANNELS_PIXEL);
        addFourBytes(file, header + 16, EACH_CHANNEL_HAS_MASK);
        addFourBytes(file, header + 20, pixelBytes);
        addFourBytes(file, header + 40, 0x00FF0000);
        addFourBytes(file, header + 44, 0x0000FF00);
        addFourBytes(file, header + 48, 0x000000FF);
        addFourBytes(file, header + 52, 0xFF000000);
        addFourBytes(file, header + 56, USUAL_COLOUR_SPACE);

        byte[] rgba = image.rgba();
        int writing = FILE_HEADER_BYTES + FIFTH_HEADER_BYTES;
        for (int at = 0; at < pixelBytes; at += CHANNELS_PIXEL) {
            file[writing++] = rgba[at + 2];
            file[writing++] = rgba[at + 1];
            file[writing++] = rgba[at];
            file[writing++] = rgba[at + 3];
        }
        return file;
    }

    private static void addFourBytes(byte[] file, int at, int value) {
        file[at] = (byte) value;
        file[at + 1] = (byte) (value >> 8);
        file[at + 2] = (byte) (value >> 16);
        file[at + 3] = (byte) (value >> 24);
    }

    private static void addTwoBytes(byte[] file, int at, int value) {
        file[at] = (byte) value;
        file[at + 1] = (byte) (value >> 8);
    }
}
