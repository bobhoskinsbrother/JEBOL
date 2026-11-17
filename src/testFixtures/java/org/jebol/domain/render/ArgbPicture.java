package org.jebol.domain.render;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;

public record ArgbPicture(int width, int height, int[] pixels) {

    private static final int OPAQUE = 0xFF000000;

    public ArgbPicture {
        if (pixels.length != width * height) {
            throw new IllegalArgumentException(
                    pixels.length + " pixels cannot be a picture " + width + " by " + height);
        }
    }

    public static ArgbPicture opaque(int width, int height, int[] rgb) {
        int[] pixels = new int[rgb.length];
        for (int at = 0; at < rgb.length; at++) {
            pixels[at] = OPAQUE | rgb[at];
        }
        return new ArgbPicture(width, height, pixels);
    }

    public static ArgbPicture readRgba(InputStream from) {
        try (DataInputStream reading = new DataInputStream(from)) {
            int width = reading.readInt();
            int height = reading.readInt();
            int[] pixels = new int[width * height];
            for (int at = 0; at < pixels.length; at++) {
                int red = reading.readUnsignedByte();
                int green = reading.readUnsignedByte();
                int blue = reading.readUnsignedByte();
                int alpha = reading.readUnsignedByte();
                pixels[at] = alpha << 24 | red << 16 | green << 8 | blue;
            }
            return new ArgbPicture(width, height, pixels);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    public byte[] writeRgba() {
        ByteArrayOutputStream written = new ByteArrayOutputStream(8 + pixels.length * 4);
        try (DataOutputStream writing = new DataOutputStream(written)) {
            writing.writeInt(width);
            writing.writeInt(height);
            for (int pixel : pixels) {
                writing.writeByte(pixel >>> 16 & 0xFF);
                writing.writeByte(pixel >>> 8 & 0xFF);
                writing.writeByte(pixel & 0xFF);
                writing.writeByte(pixel >>> 24);
            }
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        return written.toByteArray();
    }

    public int rgbAt(int across, int down) {
        return pixels[down * width + across] & 0xFFFFFF;
    }

    public long countColours() {
        return Arrays.stream(pixels).map(each -> each & 0xFFFFFF).distinct().count();
    }
}
