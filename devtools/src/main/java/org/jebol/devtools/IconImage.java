package org.jebol.devtools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class IconImage {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private IconImage() {
    }

    public static boolean isASquarePngOfAtLeast(Path file, int smallest) {
        if (!isAPng(file)) {
            return false;
        }
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            return image != null && image.getWidth() == image.getHeight() && image.getWidth() >= smallest;
        } catch (IOException unreadable) {
            return false;
        }
    }

    public static boolean isAPng(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return Arrays.equals(in.readNBytes(PNG_SIGNATURE.length), PNG_SIGNATURE);
        } catch (IOException unreadable) {
            return false;
        }
    }

    public static BufferedImage read(Path file) {
        try {
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                throw new IOException(file + " is not an image Java can read");
            }
            return image;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
