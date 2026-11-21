package org.jebol.devtools;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class IconSizes {

    private IconSizes() {
    }

    public static BufferedImage scaled(BufferedImage source, int size) {
        BufferedImage current = asArgb(source);
        while (current.getWidth() / 2 >= size) {
            current = drawInto(current, current.getWidth() / 2, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (current.getWidth() == size) {
            return current;
        }
        return drawInto(current, size, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage asArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D painting = converted.createGraphics();
        painting.drawImage(source, 0, 0, null);
        painting.dispose();
        return converted;
    }

    private static BufferedImage drawInto(BufferedImage source, int size, Object interpolation) {
        BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D painting = target.createGraphics();
        painting.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        painting.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        painting.drawImage(source, 0, 0, size, size, null);
        painting.dispose();
        return target;
    }

    public static byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            ImageIO.write(image, "png", encoded);
            return encoded.toByteArray();
        } catch (IOException unencoded) {
            throw new UncheckedIOException(unencoded);
        }
    }

    public static void writePng(BufferedImage source, int size, Path target) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.write(target, encodePng(scaled(source, size)));
        } catch (IOException unwritten) {
            throw new UncheckedIOException(unwritten);
        }
    }
}
