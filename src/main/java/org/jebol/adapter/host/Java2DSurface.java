package org.jebol.adapter.host;

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

import static java.awt.Font.*;

public final class Java2DSurface implements Surface {

    private static final int OPAQUE = Placement.OPAQUE;
    private static final float THE_MITRE_LIMIT_BOTH_TOOLKITS_START_AT = 10;

    public static Font faceFrom(byte[] font) {
        try {
            return Font.createFont(TRUETYPE_FONT, new ByteArrayInputStream(font));
        } catch (FontFormatException | IOException unreadable) {
            throw new IllegalStateException(
                    "these bytes are not a font a toolkit can draw with", unreadable);
        }
    }

    public static void paint(
            Graphics2D target, GobValue gob, TextMeasurement measure, Font face) {
        PaintList.of(gob).executeOn(new Java2DSurface(target, face), measure);
    }

    public static void execute(
            Graphics2D onto, PaintList painting, TextMeasurement measure, Font face) {
        painting.executeOn(new Java2DSurface(onto, face), measure);
    }

    public static void paintTheContentsOfLeavingItsTitleToTheTitleBar(
            Graphics2D onto, GobValue window, ObjectValue drawDialect,
            TextMeasurement measure, Font face) {
        PaintList.ofAWindow(window, drawDialect).executeOn(new Java2DSurface(onto, face), measure);
    }

    private final Graphics2D onto;
    private final Font face;

    private Java2DSurface(Graphics2D onto, Font face) {
        this.onto = onto;
        this.face = face;
    }

    @Override
    public void execute(DrawingCommand call) {
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
            confineToShape(own, call);
            applyTransparency(own, where.opacity());
            switch (call) {
                case DrawingCommand.Fill fill -> fill(own, fill);
                case DrawingCommand.Stroke stroke -> stroke(own, stroke);
                case DrawingCommand.Picture shown -> show(own, shown);
                case DrawingCommand.Text text -> write(own, text);
            }
        } finally {
            own.dispose();
        }
    }

    private  void confineTo(Graphics2D onto, ClipRectangle area) {
        onto.setClip(area.across(), area.down(), area.wide(), area.high());
    }

    private void confineToShape(Graphics2D target, DrawingCommand command) {
        if (command.where().clipShape().isEmpty()) {
            return;
        }
        Graphics2D graphics = (Graphics2D) target.create();
        try {
            graphics.transform(convertToAwtTransform(transform(command)));
            target.clip(graphics.getTransform().createTransformedShape(
                    buildPath(command.where().clipShape(), PaintState.AT_THE_START.fillRule())));
        } finally {
            graphics.dispose();
        }
    }

    private Transform transform(DrawingCommand command) {
        return switch (command) {
            case DrawingCommand.Fill fill -> fill.transform();
            case DrawingCommand.Stroke stroke -> stroke.transform();
            case DrawingCommand.Picture picture -> picture.transform();
            case DrawingCommand.Text ignored -> Transform.NONE;
        };
    }

    private void applyTransparency(Graphics2D target, int opacity) {
        if (opacity >= OPAQUE) {
            return;
        }
        target.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, opacity / (float) OPAQUE));
    }

    private void fill(Graphics2D target, DrawingCommand.Fill fill) {
        target.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                fill.antiAliased()
                        ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);
        target.transform(convertToAwtTransform(fill.transform()));
        Path2D.Double path = buildPath(fill.path(), fill.rule());
        fill.gradient().ifPresentOrElse(
                gradient -> {
                    target.setPaint(convertToAwtPaint(gradient));
                    target.fill(path);
                },
                () -> {
                    target.setColor(convertToAwtColour(fill.colour()));
                    target.fill(path);
                });
    }

    private void stroke(Graphics2D onto, DrawingCommand.Stroke stroke) {
        PaintState painted = stroke.painted();
        onto.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                painted.isAntiAlias()
                        ? RenderingHints.VALUE_ANTIALIAS_ON
                        : RenderingHints.VALUE_ANTIALIAS_OFF);
        onto.transform(convertToAwtTransform(stroke.transform()));
        onto.setColor(convertToAwtColour(painted.strokeColour().orElseThrow()));
        onto.setStroke(convertToAwtStroke(painted));
        onto.draw(buildPath(stroke.path(), painted.fillRule()));
    }

    private void show(Graphics2D onto, DrawingCommand.Picture shown) {
        onto.transform(convertToAwtTransform(shown.transform()));
        onto.drawImage(asAwtImage(shown.image()),
                shown.where().across(), shown.where().down(),
                (int) Math.round(shown.wide()), (int) Math.round(shown.high()), null);
    }

    private void write(Graphics2D onto, DrawingCommand.Text text) {
        PlacedText placed = text.text();
        if (!placed.selection().isEmpty()) {
            onto.setColor(convertToAwtColour(PlacedText.THE_COLOUR_A_SELECTION_IS_MARKED_IN));
            for (PlacedText.PlacedPatch patch : placed.selection()) {
                fillBetween(onto, patch.across(), patch.top(),
                        patch.across() + patch.wide(), patch.top() + patch.high());
            }
        }
        placed.shadow().ifPresent(shadow -> writeAll(
                onto, placed.lines(), shadow.across(), shadow.down(),
                convertToAwtColour(shadow.colour())));
        writeAll(onto, placed.lines(), 0, 0, null);
        placed.caret().ifPresent(caret -> {
            onto.setColor(convertToAwtColour(caret.colour()));
            fillBetween(onto, caret.across(), caret.top(),
                    caret.across() + caret.wide(), caret.top() + caret.high());
        });
    }

    private  void writeAll(
            Graphics2D onto, List<PlacedText.PlacedLine> lines,
            int movedAcross, int movedDown, Color everyRunIn) {

        for (PlacedText.PlacedLine line : lines) {
            for (PlacedText.PlacedPiece piece : line.pieces()) {
                onto.setFont(convertToAwtFont(piece.font()));
                onto.setColor(everyRunIn != null ? everyRunIn : convertToAwtColour(piece.font().colour()));
                onto.drawString(piece.text(),
                        (float) piece.across() + movedAcross,
                        (float) piece.baseline() + movedDown);
            }
        }
    }

    private void fillBetween(
            Graphics2D target, double left, double top, double right, double bottom) {
        target.fill(new Rectangle2D.Double(
                left, top, Math.max(0, right - left), Math.max(0, bottom - top)));
    }

    private  Font convertToAwtFont(TextRun text) {
        return face.deriveFont(
                (text.bold() ? BOLD : PLAIN) | (text.italic() ? ITALIC : PLAIN),
                (float) text.size());
    }

    private Paint convertToAwtPaint(Gradient gradient) {
        float[] stops = new float[gradient.stops().size()];
        Color[] colours = new Color[gradient.colours().size()];
        for (int at = 0; at < stops.length; at++) {
            stops[at] = (float) Math.min(1, Math.max(at / (float) stops.length,
                    gradient.stops().get(at)));
            colours[at] = convertToAwtColour(gradient.colours().get(at));
        }
        if (gradient.radial()) {
            return new RadialGradientPaint(
                    new Point2D.Double(
                            gradient.acrossOffset(), gradient.downOffset()),
                    (float) gradient.measureRadius(), stops, colours);
        }
        return new LinearGradientPaint(
                new Point2D.Double(
                        gradient.acrossStart(), gradient.downStart()),
                new Point2D.Double(
                        gradient.acrossEnd(), gradient.downEnd()),
                stops, colours);
    }

    private  Path2D.Double buildPath(List<PathStep> steps, FillRule rule) {
        Path2D.Double path = new Path2D.Double(
                rule == FillRule.EVEN_ODD ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
        for (PathStep step : steps) {
            addPathStep(path, step);
        }
        return path;
    }

    private void addPathStep(Path2D.Double path, PathStep step) {
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

    private  void lineOrMoveToBecauseJava2dRefusesALineOnAnEmptyPath(
            Path2D.Double path, PathStep.LineTo to) {
        if (path.getCurrentPoint() == null) {
            path.moveTo(to.across(), to.down());
            return;
        }
        path.lineTo(to.across(), to.down());
    }

    private  BasicStroke convertToAwtStroke(PaintState painted) {
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

    private  AffineTransform convertToAwtTransform(Transform transform) {
        return new AffineTransform(
                transform.acrossScale(), transform.downSkew(),
                transform.acrossSkew(), transform.downScale(),
                transform.acrossMove(), transform.downMove());
    }

    private  Color convertToAwtColour(Colour colour) {
        return new Color(colour.red(), colour.green(), colour.blue(), colour.opacity());
    }

     BufferedImage asAwtImage(ImageValue pixels) {
        PairValue size = pixels.size();
        int wide = Math.max(1, (int) Math.round(size.x()));
        int high = Math.max(1, (int) Math.round(size.y()));
        BufferedImage drawable =
                new BufferedImage(wide, high, BufferedImage.TYPE_INT_ARGB);
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                int[] parts = pixels.readPixelAt(down * wide + across + 1);
                drawable.setRGB(across, down, new Color(
                        parts[0], parts[1], parts[2],
                        parts.length >= 4 ? parts[3] : OPAQUE).getRGB());
            }
        }
        return drawable;
    }
}
