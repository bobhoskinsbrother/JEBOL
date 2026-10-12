package org.jebol.adapter.host;

import org.jebol.application.TextMeasures;
import org.jebol.domain.render.*;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PairValue;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class Java2DSurface implements Surface {

    private static final int OPAQUE = Placement.OPAQUE;
    private static final Font THE_FONT_TEXT_IS_WRITTEN_IN = theCarriedFace();
    private static final float THE_MITRE_LIMIT_BOTH_TOOLKITS_START_AT = 10;

    public static void paint(Graphics2D onto, GobValue gob, TextMeasure measure) {
        PaintList.of(gob).executeOn(new Java2DSurface(onto), measure);
    }

    public static void execute(Graphics2D onto, PaintList painting, TextMeasure measure) {
        painting.executeOn(new Java2DSurface(onto), measure);
    }

    public static void paintTheContentsOfLeavingItsTitleToTheTitleBar(
            Graphics2D onto, GobValue window, ObjectValue drawDialect, TextMeasure measure) {
        PaintList.ofAWindow(window, drawDialect).executeOn(new Java2DSurface(onto), measure);
    }

    private final Graphics2D onto;

    private Java2DSurface(Graphics2D onto) {
        this.onto = onto;
    }

    @Override
    public void execute(SurfaceCall call) {
        Placement where = call.where();
        if (where.showsNothing()) {
            return;
        }
        Graphics2D own = (Graphics2D) onto.create();
        try {
            own.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            own.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            confineTo(own, where.clip());
            confineToTheShape(own, call);
            applyTransparency(own, where.opacity());
            switch (call) {
                case SurfaceCall.Filled filled -> fill(own, filled);
                case SurfaceCall.Stroked stroked -> stroke(own, stroked);
                case SurfaceCall.Pictured shown -> show(own, shown);
                case SurfaceCall.Written written -> write(own, written);
            }
        } finally {
            own.dispose();
        }
    }

    private static void confineTo(Graphics2D onto, ClipRectangle area) {
        onto.setClip(area.across(), area.down(), area.wide(), area.high());
    }

    private static void confineToTheShape(Graphics2D onto, SurfaceCall call) {
        if (call.where().clipShape().isEmpty()) {
            return;
        }
        Graphics2D underTheTransform = (Graphics2D) onto.create();
        try {
            underTheTransform.transform(javaTransformOf(transformOf(call)));
            onto.clip(underTheTransform.getTransform().createTransformedShape(
                    pathFrom(call.where().clipShape(), PaintState.AT_THE_START.fillRule())));
        } finally {
            underTheTransform.dispose();
        }
    }

    private static Transform transformOf(SurfaceCall call) {
        return switch (call) {
            case SurfaceCall.Filled filled -> filled.transform();
            case SurfaceCall.Stroked stroked -> stroked.transform();
            case SurfaceCall.Pictured shown -> shown.transform();
            case SurfaceCall.Written ignored -> Transform.NONE;
        };
    }

    private static void applyTransparency(Graphics2D onto, int opacity) {
        if (opacity >= OPAQUE) {
            return;
        }
        onto.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, opacity / (float) OPAQUE));
    }

    private static void fill(Graphics2D onto, SurfaceCall.Filled filled) {
        onto.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                filled.antiAliased()
                        ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);
        onto.transform(javaTransformOf(filled.transform()));
        Path2D.Double path = pathFrom(filled.path(), filled.rule());
        filled.gradient().ifPresentOrElse(
                gradient -> {
                    onto.setPaint(javaPaintOf(gradient));
                    onto.fill(path);
                },
                () -> {
                    onto.setColor(javaColourOf(filled.colour()));
                    onto.fill(path);
                });
    }

    private static void stroke(Graphics2D onto, SurfaceCall.Stroked stroked) {
        PaintState painted = stroked.painted();
        onto.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                painted.antiAliased()
                        ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);
        onto.transform(javaTransformOf(stroked.transform()));
        onto.setColor(javaColourOf(painted.strokeColour().orElseThrow()));
        onto.setStroke(javaStrokeOf(painted));
        onto.draw(pathFrom(stroked.path(), painted.fillRule()));
    }

    private static void show(Graphics2D onto, SurfaceCall.Pictured shown) {
        onto.transform(javaTransformOf(shown.transform()));
        onto.drawImage(asJavaImage(shown.image()),
                shown.where().across(), shown.where().down(),
                (int) Math.round(shown.wide()), (int) Math.round(shown.high()), null);
    }

    private static void write(Graphics2D onto, SurfaceCall.Written written) {
        PlacedText placed = written.text();
        if (!placed.selection().isEmpty()) {
            onto.setColor(javaColourOf(PlacedText.THE_COLOUR_A_SELECTION_IS_MARKED_IN));
            for (PlacedText.PlacedPatch patch : placed.selection()) {
                fillBetween(onto, patch.across(), patch.top(),
                        patch.across() + patch.wide(), patch.top() + patch.high());
            }
        }
        placed.shadow().ifPresent(shadow -> writeThePieces(
                onto, placed.lines(), shadow.across(), shadow.down(),
                javaColourOf(shadow.colour())));
        writeThePieces(onto, placed.lines(), 0, 0, null);
        placed.caret().ifPresent(caret -> {
            onto.setColor(javaColourOf(caret.colour()));
            fillBetween(onto, caret.across(), caret.top(),
                    caret.across() + caret.wide(), caret.top() + caret.high());
        });
    }

    private static void writeThePieces(
            Graphics2D onto, List<PlacedText.PlacedLine> lines,
            int movedAcross, int movedDown, Color everyRunIn) {

        for (PlacedText.PlacedLine line : lines) {
            for (PlacedText.PlacedPiece piece : line.pieces()) {
                onto.setFont(javaFontOf(piece.font()));
                onto.setColor(everyRunIn != null ? everyRunIn : javaColourOf(piece.font().colour()));
                onto.drawString(piece.text(),
                        (float) piece.across() + movedAcross,
                        (float) piece.baseline() + movedDown);
            }
        }
    }

    private static void fillBetween(
            Graphics2D onto, double left, double top, double right, double bottom) {
        onto.fill(new Rectangle2D.Double(
                left, top, Math.max(0, right - left), Math.max(0, bottom - top)));
    }

    private static Font theCarriedFace() {
        try {
            return Font.createFont(Font.TRUETYPE_FONT,
                    new ByteArrayInputStream(TextMeasures.theCarriedFontBytes()));
        } catch (FontFormatException | IOException unreadable) {
            throw new IllegalStateException(
                    "the font this build carries would not load", unreadable);
        }
    }

    private static Font javaFontOf(TextRun run) {
        return THE_FONT_TEXT_IS_WRITTEN_IN.deriveFont(
                (run.bold() ? Font.BOLD : 0) | (run.italic() ? Font.ITALIC : 0),
                (float) run.size());
    }

    private static Paint javaPaintOf(Gradient gradient) {
        float[] stops = new float[gradient.stops().size()];
        Color[] colours = new Color[gradient.colours().size()];
        for (int at = 0; at < stops.length; at++) {
            stops[at] = (float) Math.min(1, Math.max(at / (float) stops.length,
                    gradient.stops().get(at)));
            colours[at] = javaColourOf(gradient.colours().get(at));
        }
        if (gradient.radial()) {
            return new RadialGradientPaint(
                    new Point2D.Double(
                            gradient.acrossOffset(), gradient.downOffset()),
                    (float) gradient.radius(), stops, colours);
        }
        return new LinearGradientPaint(
                new Point2D.Double(
                        gradient.acrossStart(), gradient.downStart()),
                new Point2D.Double(
                        gradient.acrossEnd(), gradient.downEnd()),
                stops, colours);
    }

    private static Path2D.Double pathFrom(List<PathStep> steps, FillRule rule) {
        Path2D.Double path = new Path2D.Double(
                rule == FillRule.EVEN_ODD ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
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

    private static Color javaColourOf(Colour colour) {
        return new Color(colour.red(), colour.green(), colour.blue(), colour.opacity());
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
