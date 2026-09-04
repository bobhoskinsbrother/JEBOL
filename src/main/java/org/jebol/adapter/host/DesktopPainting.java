package org.jebol.adapter.host;

import org.jebol.domain.render.*;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.PairValue;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

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
    static void paintTheContentsOf(
            Graphics2D onto, GobValue window,
            org.jebol.domain.value.ObjectValue drawDialect) {

        execute(onto, PaintList.ofAWindow(window, drawDialect));
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
                case PaintInstruction.Drawn drawing -> draw(own, drawing);
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

    /**
     * A path, filled then stroked, under whatever transform it carried.
     *
     * <p>Filled before stroked because a stroke straddles the outline: half of
     * it lies inside the shape, so filling afterwards would paint over the
     * inner half of every line and make every stroke look half as wide as it
     * was asked to be.
     */
    private static void draw(Graphics2D onto, PaintInstruction.Drawn drawing) {
        Path2D.Double path = pathFrom(drawing.path(), drawing.painted());
        onto.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                drawing.painted().antiAliased()
                        ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);
        onto.transform(javaTransformOf(drawing.transform()));

        drawing.painted().fillColour().ifPresent(colour -> {
            onto.setColor(javaColourOf(colour));
            onto.fill(path);
        });
        drawing.painted().strokeColour().ifPresent(colour -> {
            onto.setColor(javaColourOf(colour));
            onto.setStroke(javaStrokeOf(drawing.painted()));
            onto.draw(path);
        });
    }

    private static Path2D.Double pathFrom(
            List<PathStep> steps, PaintState painted) {

        Path2D.Double path = new Path2D.Double(
                painted.fillRule() == FillRule.EVEN_ODD
                        ? Path2D.WIND_EVEN_ODD
                        : Path2D.WIND_NON_ZERO);
        for (PathStep step : steps) {
            obeyOnThePath(path, step);
        }
        return path;
    }

    private static void obeyOnThePath(Path2D.Double path, PathStep step) {
        switch (step) {
            case PathStep.MoveTo to -> path.moveTo(to.across(), to.down());
            case PathStep.LineTo to -> lineOrMoveTo(path, to);
            case PathStep.QuadraticTo to -> path.quadTo(
                    to.controlAcross(), to.controlDown(), to.across(), to.down());
            case PathStep.CubicTo to -> path.curveTo(
                    to.firstControlAcross(), to.firstControlDown(),
                    to.secondControlAcross(), to.secondControlDown(),
                    to.across(), to.down());
            case PathStep.EllipseAt ellipse -> path.append(new Ellipse2D.Double(
                    ellipse.centreAcross() - ellipse.radiusAcross(),
                    ellipse.centreDown() - ellipse.radiusDown(),
                    ellipse.radiusAcross() * 2, ellipse.radiusDown() * 2), false);
            case PathStep.ArcTo arc -> path.append(new Arc2D.Double(
                    arc.centreAcross() - arc.radiusAcross(),
                    arc.centreDown() - arc.radiusDown(),
                    arc.radiusAcross() * 2, arc.radiusDown() * 2,
                    -arc.beginsAt(), -arc.turnsThrough(),
                    arc.closes() ? Arc2D.PIE : Arc2D.OPEN), false);
            case PathStep.Close ignored -> path.closePath();
        }
    }

    /**
     * A line with nothing before it starts the path instead of raising.
     *
     * <p>Java2D refuses a {@code lineTo} on an empty path, and a draw block
     * that opens with one is a person's mistake rather than something worth
     * ending the picture over.
     */
    private static void lineOrMoveTo(Path2D.Double path, PathStep.LineTo to) {
        if (path.getCurrentPoint() == null) {
            path.moveTo(to.across(), to.down());
            return;
        }
        path.lineTo(to.across(), to.down());
    }

    private static BasicStroke javaStrokeOf(PaintState painted) {
        return new BasicStroke((float) painted.lineWidth(),
                switch (painted.lineCap()) {
                    case BUTT -> BasicStroke.CAP_BUTT;
                    case SQUARE -> BasicStroke.CAP_SQUARE;
                    case ROUNDED -> BasicStroke.CAP_ROUND;
                },
                switch (painted.lineJoin()) {
                    case MITER, MITER_BEVEL -> BasicStroke.JOIN_MITER;
                    case ROUND -> BasicStroke.JOIN_ROUND;
                    case BEVEL -> BasicStroke.JOIN_BEVEL;
                });
    }

    private static AffineTransform javaTransformOf(Transform transform) {
        return new AffineTransform(
                transform.acrossScale(), transform.downSkew(),
                transform.acrossSkew(), transform.downScale(),
                transform.acrossMove(), transform.downMove());
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
