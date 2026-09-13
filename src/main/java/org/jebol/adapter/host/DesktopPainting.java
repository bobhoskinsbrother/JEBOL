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
 * <p>Apart from the surface it knows nothing about windows, so it paints onto a
 * window, onto an image, or onto anything else Java2D can draw on.
 */
public final class DesktopPainting {

    private static final int OPAQUE = Placement.OPAQUE;
    private static final Font TEXT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private static final int WRITING_INSET = 2;

    private DesktopPainting() {
    }

    static void paint(Graphics2D onto, GobValue gob) {
        execute(onto, PaintList.of(gob));
    }

    static void paintTheContentsOfLeavingItsTitleToTheTitleBar(
            Graphics2D onto, GobValue window,
            org.jebol.domain.value.ObjectValue drawDialect) {

        execute(onto, PaintList.ofAWindow(window, drawDialect));
    }

    /** Paints a list that was flattened somewhere else. */
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
            confineToTheShape(own, where, instruction);
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

    private static void confineToTheShape(
            Graphics2D onto, Placement where, PaintInstruction instruction) {

        if (where.clipShape().isEmpty()) {
            return;
        }
        Graphics2D underTheTransform = (Graphics2D) onto.create();
        try {
            if (instruction instanceof PaintInstruction.Drawn drawing) {
                underTheTransform.transform(javaTransformOf(drawing.transform()));
            }
            onto.clip(underTheTransform.getTransform().createTransformedShape(
                    pathFrom(where.clipShape(), PaintState.AT_THE_START)));
        } finally {
            underTheTransform.dispose();
        }
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

        Font asked = TEXT.deriveFont(
                (written.bold() ? Font.BOLD : 0) | (written.italic() ? Font.ITALIC : 0),
                (float) written.size());
        onto.setFont(asked);
        onto.setColor(javaColourOf(written.colour()));
        onto.drawString(written.text(),
                where.across() + WRITING_INSET,
                where.down() + Math.min(
                        where.high() - WRITING_INSET, asked.getSize() + WRITING_INSET));
    }

    private static void show(
            Graphics2D onto, Placement where, PaintInstruction.Picture shown) {

        onto.transform(javaTransformOf(shown.transform()));
        onto.drawImage(asJavaImage(shown.pixels()),
                where.across(), where.down(),
                (int) Math.round(shown.wide()), (int) Math.round(shown.high()), null);
    }

    private static void draw(Graphics2D onto, PaintInstruction.Drawn drawing) {
        Path2D.Double path = pathFrom(drawing.path(), drawing.painted());
        onto.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                drawing.painted().antiAliased()
                        ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);
        onto.transform(javaTransformOf(drawing.transform()));

        fillBeforeStrokingSoTheStrokeKeepsItsFullWidth(onto, drawing, path);
    }

    private static void fillBeforeStrokingSoTheStrokeKeepsItsFullWidth(
            Graphics2D onto, PaintInstruction.Drawn drawing, Path2D.Double path) {

        drawing.painted().fillGradient().ifPresentOrElse(gradient -> {
            onto.setPaint(javaPaintOf(gradient));
            onto.fill(path);
        }, () -> drawing.painted().fillColour().ifPresent(colour -> {
            onto.setColor(javaColourOf(colour));
            onto.fill(path);
        }));
        drawing.painted().strokeColour().ifPresent(colour -> {
            onto.setColor(javaColourOf(colour));
            onto.setStroke(javaStrokeOf(drawing.painted()));
            onto.draw(path);
        });
    }

    private static java.awt.Paint javaPaintOf(Gradient gradient) {
        float[] stops = new float[gradient.stops().size()];
        Color[] colours = new Color[gradient.colours().size()];
        for (int at = 0; at < stops.length; at++) {
            stops[at] = (float) Math.min(1, Math.max(at / (float) stops.length,
                    gradient.stops().get(at)));
            colours[at] = javaColourOf(gradient.colours().get(at));
        }
        if (gradient.radial()) {
            return new java.awt.RadialGradientPaint(
                    new java.awt.geom.Point2D.Double(
                            gradient.acrossOffset(), gradient.downOffset()),
                    (float) gradient.radius(), stops, colours);
        }
        return new java.awt.LinearGradientPaint(
                new java.awt.geom.Point2D.Double(
                        gradient.acrossStart(), gradient.downStart()),
                new java.awt.geom.Point2D.Double(
                        gradient.acrossEnd(), gradient.downEnd()),
                stops, colours);
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
            case PathStep.LineTo to ->
                    lineOrMoveToBecauseJava2dRefusesALineOnAnEmptyPath(path, to);
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

    private static void lineOrMoveToBecauseJava2dRefusesALineOnAnEmptyPath(
            Path2D.Double path, PathStep.LineTo to) {
        if (path.getCurrentPoint() == null) {
            path.moveTo(to.across(), to.down());
            return;
        }
        path.lineTo(to.across(), to.down());
    }

    private static final float THE_MITRE_LIMIT_BOTH_TOOLKITS_START_AT = 10;

    private static BasicStroke javaStrokeOf(PaintState painted) {
        int cap = switch (painted.lineCap()) {
            case BUTT -> BasicStroke.CAP_BUTT;
            case SQUARE -> BasicStroke.CAP_SQUARE;
            case ROUNDED -> BasicStroke.CAP_ROUND;
        };
        int join = switch (painted.lineJoin()) {
            case MITER, MITER_BEVEL -> BasicStroke.JOIN_MITER;
            case ROUND -> BasicStroke.JOIN_ROUND;
            case BEVEL -> BasicStroke.JOIN_BEVEL;
        };
        if (painted.dashes().isEmpty()) {
            return new BasicStroke((float) painted.lineWidth(), cap, join);
        }
        float[] dashes = new float[painted.dashes().size()];
        for (int at = 0; at < dashes.length; at++) {
            dashes[at] = (float) Math.max(0.01, painted.dashes().get(at));
        }
        return new BasicStroke((float) painted.lineWidth(), cap, join,
                THE_MITRE_LIMIT_BOTH_TOOLKITS_START_AT, dashes, 0);
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

    static BufferedImage asJavaImage(ImageValue pixels) {
        PairValue size = pixels.size();
        int wide = Math.max(1, (int) Math.round(size.x()));
        int high = Math.max(1, (int) Math.round(size.y()));
        BufferedImage drawable =
                new BufferedImage(wide, high, BufferedImage.TYPE_INT_ARGB);
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                int[] parts = pixels.pixelAt(down * wide + across + 1);
                drawable.setRGB(across, down, new Color(
                        parts[0], parts[1], parts[2],
                        parts.length >= 4 ? parts[3] : OPAQUE).getRGB());
            }
        }
        return drawable;
    }
}
