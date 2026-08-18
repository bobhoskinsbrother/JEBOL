package org.jebol.adapter.host;

import org.jebol.domain.value.GobStorage;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;

/**
 * Paints a gob tree onto a Java2D surface.
 *
 * <p>Apart from the surface this knows nothing about windows, so it paints the
 * same tree onto a window, onto an image, or onto anything else Java2D can
 * draw on. That is what lets the painting be tested where no display exists:
 * a {@link BufferedImage} works with {@code java.awt.headless=true}, and a
 * test can read the pixels back.
 *
 * <p>A gob paints itself and then its children, each child offset by its own
 * offset and clipped to its parent. Four of the eight content kinds paint
 * here -- a colour, a string, a rich-text block and an image. A draw block
 * paints nothing yet and that gap is named in {@code spec/screen.allium}
 * rather than hidden.
 */
final class GobPainter {

    private static final int OPAQUE = 255;
    private static final Font TEXT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private GobPainter() {
    }

    /** Paints a gob and everything under it, at the origin of the surface. */
    static void paint(Graphics2D onto, GobValue gob) {
        Graphics2D own = (Graphics2D) onto.create();
        try {
            own.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            own.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintTree(own, gob.storage(), 0, 0);
        } finally {
            own.dispose();
        }
    }

    private static void paintTree(
            Graphics2D onto, GobStorage gob, int acrossFromLeft, int downFromTop) {

        int wide = whole(gob.size().x());
        int high = whole(gob.size().y());
        if (wide <= 0 || high <= 0) {
            return;
        }
        Graphics2D own = (Graphics2D) onto.create(acrossFromLeft, downFromTop, wide, high);
        try {
            applyTransparency(own, gob.alpha());
            paintOwnContent(own, gob, wide, high);
            paintChildren(own, gob);
        } finally {
            own.dispose();
        }
    }

    private static void paintChildren(Graphics2D onto, GobStorage gob) {
        for (Value child : gob.pane()) {
            if (child instanceof GobValue held) {
                paintTree(onto, held.storage(),
                        whole(held.storage().offset().x()),
                        whole(held.storage().offset().y()));
            }
        }
    }

    private static void applyTransparency(Graphics2D onto, int alpha) {
        if (alpha >= OPAQUE) {
            return;
        }
        onto.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, alpha / (float) OPAQUE));
    }

    private static void paintOwnContent(
            Graphics2D onto, GobStorage gob, int wide, int high) {

        switch (gob.contentKind()) {
            case COLOUR -> fillWith(onto, gob.contentIfKind(GobStorage.Content.COLOUR),
                    wide, high);
            case STRING -> writeText(onto,
                    gob.contentIfKind(GobStorage.Content.STRING), high);
            case TEXT -> writeText(onto,
                    gob.contentIfKind(GobStorage.Content.TEXT), high);
            case IMAGE -> drawImage(onto, gob.contentIfKind(GobStorage.Content.IMAGE));
            case NONE, DRAW, EFFECT, WIDGET -> {
            }
        }
    }

    private static void fillWith(Graphics2D onto, Value colour, int wide, int high) {
        if (!(colour instanceof TupleValue parts)) {
            return;
        }
        onto.setColor(javaColourOf(parts));
        onto.fillRect(0, 0, wide, high);
    }

    /**
     * A gob's colour tuple as a Java colour.
     *
     * <p>The fourth octet is opacity and runs the same way Java's alpha does:
     * 255 is opaque. The C says so where it decides whether a gob can be
     * painted over -- {@code if (VAL_TUPLE_LEN(val) < 4 || VAL_TUPLE(val)[3]
     * == 255) SET_GOB_OPAQUE(gob);} -- and a tuple written with three octets
     * is stored with a fourth of 255, so a colour with no opacity named is
     * opaque.
     */
    private static Color javaColourOf(TupleValue parts) {
        int opacity = parts.segments().length >= 4 ? parts.octetAt(4) : OPAQUE;
        return new Color(parts.octetAt(1), parts.octetAt(2), parts.octetAt(3),
                opacity);
    }

    private static void writeText(Graphics2D onto, Value held, int high) {
        String written = held instanceof StringValue text
                ? text.text()
                : Molder.form(held);
        if (written.isEmpty()) {
            return;
        }
        onto.setFont(TEXT);
        onto.setColor(Color.BLACK);
        onto.drawString(written, 2, Math.min(high - 2, TEXT.getSize() + 2));
    }

    private static void drawImage(Graphics2D onto, Value held) {
        if (!(held instanceof ImageValue pixels)) {
            return;
        }
        onto.drawImage(asJavaImage(pixels), 0, 0, null);
    }

    /** A REBOL image as one Java can draw, a pixel at a time. */
    static BufferedImage asJavaImage(ImageValue pixels) {
        PairValue size = pixels.size();
        int wide = Math.max(1, whole(size.x()));
        int high = Math.max(1, whole(size.y()));
        BufferedImage drawable =
                new BufferedImage(wide, high, BufferedImage.TYPE_INT_ARGB);
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                int[] parts = pixels.pixelAt(down * wide + across);
                drawable.setRGB(across, down, new Color(
                        parts[0], parts[1], parts[2],
                        parts.length >= 4 ? parts[3] : OPAQUE).getRGB());
            }
        }
        return drawable;
    }

    /** A gob's sizes and offsets are float pixels; a surface wants whole ones. */
    private static int whole(double measurement) {
        return (int) Math.round(measurement);
    }

    /** The area a gob covers, for a test or a repaint to ask about. */
    static Shape areaOf(GobValue gob) {
        return new java.awt.Rectangle(
                whole(gob.storage().offset().x()), whole(gob.storage().offset().y()),
                whole(gob.storage().size().x()), whole(gob.storage().size().y()));
    }
}
