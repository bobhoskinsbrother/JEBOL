package org.jebol.adapter.host;

import org.jebol.domain.render.ClipRectangle;
import org.jebol.domain.render.PaintInstruction;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.Placement;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.PairValue;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Executes a paint list on a Java2D surface.
 *
 * <p>It walks no gob tree, adds up no offsets and works out no clip. Every
 * one of those decisions was made once in {@link PaintList}, which is what
 * keeps this and a browser and a phone showing the same picture: they are not
 * three walks that happen to agree, they are three executions of one list.
 *
 * <p>Apart from the surface it knows nothing about windows, so it paints onto
 * a window, onto an image, or onto anything else Java2D can draw on. That is
 * what lets the painting be tested where no display exists -- a
 * {@link BufferedImage} works with {@code java.awt.headless=true}, and a test
 * can read the pixels back.
 */
public final class DesktopPainting {

    private static final int OPAQUE = Placement.OPAQUE;
    private static final Font TEXT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    /** How far in from a gob's corner a line of writing starts. */
    private static final int WRITING_INSET = 2;

    private DesktopPainting() {
    }

    /** Paints a gob and everything under it, at the origin of the surface. */
    static void paint(Graphics2D onto, GobValue gob) {
        execute(onto, PaintList.of(gob));
    }

    /**
     * Paints one window's contents, leaving its title to the title bar.
     *
     * <p>A window gob's {@code text} is the words VIEW put there for the title
     * bar, and painting them as content writes the window's own title across
     * its top left corner in black. That was happening and nobody noticed,
     * because no test gob had any text until a browser rendered a real VIEW.
     */
    static void paintTheContentsOf(Graphics2D onto, GobValue window) {
        execute(onto, PaintList.ofAWindow(window));
    }

    /**
     * Paints a list that was flattened somewhere else.
     *
     * <p>Public because a paint list is the currency between renderers, and
     * anything that has one and a {@code Graphics2D} can draw the same picture
     * a window would: a report, a printer, an image on disk, or a test holding
     * this against what a browser drew.
     */
    public static void execute(Graphics2D onto, PaintList painting) {
        Graphics2D own = (Graphics2D) onto.create();
        try {
            own.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            own.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            painting.instructions().forEach(instruction -> obey(own, instruction));
        } finally {
            own.dispose();
        }
    }

    private static void obey(Graphics2D onto, PaintInstruction instruction) {
        Placement where = instruction.where();
        if (where.showsNothing()) {
            return;
        }
        Graphics2D own = (Graphics2D) onto.create();
        try {
            confineTo(own, where.clip());
            applyTransparency(own, where.opacity());
            switch (instruction) {
                case PaintInstruction.Fill filled -> fill(own, where, filled);
                case PaintInstruction.Writing written -> write(own, where, written);
                case PaintInstruction.Picture shown -> show(own, where, shown);
            }
        } finally {
            own.dispose();
        }
    }

    private static void confineTo(Graphics2D onto, ClipRectangle area) {
        onto.setClip(area.across(), area.down(), area.wide(), area.high());
    }

    private static void applyTransparency(Graphics2D onto, int opacity) {
        if (opacity >= OPAQUE) {
            return;
        }
        onto.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, opacity / (float) OPAQUE));
    }

    private static void fill(
            Graphics2D onto, Placement where, PaintInstruction.Fill filled) {

        onto.setColor(javaColourOf(filled.colour()));
        onto.fillRect(where.across(), where.down(), where.wide(), where.high());
    }

    private static void write(
            Graphics2D onto, Placement where, PaintInstruction.Writing written) {

        onto.setFont(TEXT);
        onto.setColor(javaColourOf(written.colour()));
        onto.drawString(written.text(),
                where.across() + WRITING_INSET,
                where.down() + Math.min(
                        where.high() - WRITING_INSET, TEXT.getSize() + WRITING_INSET));
    }

    private static void show(
            Graphics2D onto, Placement where, PaintInstruction.Picture shown) {

        onto.drawImage(asJavaImage(shown.pixels()), where.across(), where.down(), null);
    }

    private static Color javaColourOf(org.jebol.domain.render.Colour colour) {
        return new Color(colour.red(), colour.green(), colour.blue());
    }

    /** A REBOL image as one Java can draw, a pixel at a time. */
    static BufferedImage asJavaImage(ImageValue pixels) {
        PairValue size = pixels.size();
        int wide = Math.max(1, (int) Math.round(size.x()));
        int high = Math.max(1, (int) Math.round(size.y()));
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
}
