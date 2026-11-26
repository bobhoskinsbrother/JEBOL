package org.jebol.adapter.host;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public final class AppIcon {

    private final BufferedImage picture;

    private AppIcon(BufferedImage picture) {
        this.picture = picture;
    }

    public static AppIcon readFrom(Path file) {
        try {
            BufferedImage read = ImageIO.read(file.toFile());
            if (read == null) {
                throw new IllegalArgumentException(file + " is not a picture this machine can read");
            }
            return new AppIcon(read);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    BufferedImage drawAt(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("an icon of " + size + " pixels has no picture to draw");
        }
        BufferedImage drawn = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D painting = drawn.createGraphics();
        try {
            painting.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            painting.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            painting.drawImage(picture, 0, 0, size, size, null);
        } finally {
            painting.dispose();
        }
        return drawn;
    }
}
