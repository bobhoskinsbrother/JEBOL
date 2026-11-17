package org.jebol.adapter.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;

import org.jebol.domain.render.ClipRectangle;
import org.jebol.domain.render.Colour;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.render.FillRule;
import org.jebol.domain.render.Gradient;
import org.jebol.domain.render.LineCap;
import org.jebol.domain.render.LineJoin;
import org.jebol.domain.render.PaintState;
import org.jebol.domain.render.PathStep;
import org.jebol.domain.render.Placement;
import org.jebol.domain.render.PlacedText;
import org.jebol.domain.render.Surface;
import org.jebol.domain.render.TextRun;
import org.jebol.domain.render.Transform;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.PairValue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AndroidSurface implements Surface {

    private static final int OPAQUE = Placement.OPAQUE;
    private static final float MITRE_LIMIT_BOTH_TOOLKITS_START_AT = 10;
    private static final float ITALIC_SLANT = -0.25f;

    private static final Map<LineCap, Paint.Cap> CAPS = new EnumMap<>(Map.of(
            LineCap.BUTT, Paint.Cap.BUTT,
            LineCap.SQUARE, Paint.Cap.SQUARE,
            LineCap.ROUNDED, Paint.Cap.ROUND));

    private static final Map<LineJoin, Paint.Join> JOINS = new EnumMap<>(Map.of(
            LineJoin.MITER, Paint.Join.MITER,
            LineJoin.MITER_BEVEL, Paint.Join.MITER,
            LineJoin.ROUND, Paint.Join.ROUND,
            LineJoin.BEVEL, Paint.Join.BEVEL));

    private final Canvas onto;
    private final Typeface face;
    private int opacity = OPAQUE;

    public AndroidSurface(Canvas onto, Typeface face) {
        this.onto = onto;
        this.face = face;
    }

    public static Typeface faceFrom(byte[] font) {
        ByteBuffer held = ByteBuffer.allocateDirect(font.length);
        held.put(font).flip();
        try {
            FontFamily family = new FontFamily.Builder(new Font.Builder(held).build()).build();
            return new Typeface.CustomFallbackBuilder(family).build();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("these bytes are not a font Android can draw with", unreadable);
        }
    }

    @Override
    public void execute(DrawingCommand call) {
        Placement where = call.where();
        if (where.showsNothing()) {
            return;
        }
        int saved = onto.save();
        try {
            confineTo(where.clip());
            confineToShape(call);
            opacity = where.opacity();
            if (call instanceof DrawingCommand.Fill fill) {
                fill(fill);
            } else if (call instanceof DrawingCommand.Stroke stroke) {
                stroke(stroke);
            } else if (call instanceof DrawingCommand.Picture shown) {
                show(shown);
            } else if (call instanceof DrawingCommand.Text text) {
                write(text);
            }
        } finally {
            onto.restoreToCount(saved);
            opacity = OPAQUE;
        }
    }

    private void confineTo(ClipRectangle area) {
        onto.clipRect(area.across(), area.down(), area.across() + area.wide(), area.down() + area.high());
    }

    private void confineToShape(DrawingCommand command) {
        if (command.where().clipShape().isEmpty()) {
            return;
        }
        Path shape = buildPath(command.where().clipShape(), PaintState.AT_START.fillRule());
        shape.transform(convertToMatrix(transform(command)));
        onto.clipPath(shape);
    }

    private static Transform transform(DrawingCommand command) {
        if (command instanceof DrawingCommand.Fill fill) {
            return fill.transform();
        }
        if (command instanceof DrawingCommand.Stroke stroke) {
            return stroke.transform();
        }
        if (command instanceof DrawingCommand.Picture picture) {
            return picture.transform();
        }
        return ((DrawingCommand.Text) command).transform();
    }

    private void fill(DrawingCommand.Fill fill) {
        onto.concat(convertToMatrix(fill.transform()));
        Paint paint = new Paint();
        paint.setAntiAlias(fill.antiAliased());
        paint.setStyle(Paint.Style.FILL);
        fill.gradient().ifPresentOrElse(
                gradient -> {
                    paint.setShader(convertToShader(gradient));
                    paint.setAlpha(opacity);
                },
                () -> paint.setColor(convertToColour(fill.colour())));
        onto.drawPath(buildPath(fill.path(), fill.rule()), paint);
    }

    private void stroke(DrawingCommand.Stroke stroke) {
        PaintState painted = stroke.painted();
        onto.concat(convertToMatrix(stroke.transform()));
        Paint paint = new Paint();
        paint.setAntiAlias(painted.isAntiAlias());
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(convertToColour(painted.strokeColour().orElseThrow()));
        paint.setStrokeWidth((float) painted.lineWidth());
        paint.setStrokeCap(CAPS.get(painted.lineCap()));
        paint.setStrokeJoin(JOINS.get(painted.lineJoin()));
        paint.setStrokeMiter(MITRE_LIMIT_BOTH_TOOLKITS_START_AT);
        if (!painted.dashes().isEmpty()) {
            paint.setPathEffect(new DashPathEffect(convertToDashes(painted.dashes()), 0));
        }
        onto.drawPath(buildPath(stroke.path(), painted.fillRule()), paint);
    }

    private static float[] convertToDashes(List<Double> dashes) {
        int count = dashes.size() % 2 == 0 ? dashes.size() : dashes.size() * 2;
        float[] lengths = new float[count];
        for (int at = 0; at < count; at++) {
            lengths[at] = (float) Math.max(0.01, dashes.get(at % dashes.size()));
        }
        return lengths;
    }

    private void show(DrawingCommand.Picture shown) {
        onto.concat(convertToMatrix(shown.transform()));
        Placement where = shown.where();
        Rect target = new Rect(where.across(), where.down(),
                where.across() + (int) Math.round(shown.wide()), where.down() + (int) Math.round(shown.high()));
        Paint paint = new Paint();
        paint.setFilterBitmap(false);
        paint.setAlpha(opacity);
        onto.drawBitmap(convertToBitmap(shown.image()), null, target, paint);
    }

    private void write(DrawingCommand.Text text) {
        onto.concat(convertToMatrix(text.transform()));
        PlacedText placed = text.text();
        if (!placed.selection().isEmpty()) {
            Paint marked = solidPaint(PlacedText.COLOUR_SELECTION_IS_MARKED_IN);
            for (PlacedText.PlacedPatch patch : placed.selection()) {
                fillBetween(patch.across(), patch.top(), patch.across() + patch.wide(), patch.top() + patch.high(),
                        marked);
            }
        }
        placed.shadow().ifPresent(shadow -> writeAll(placed.lines(), shadow.across(), shadow.down(),
                convertToColour(shadow.colour()), true));
        writeAll(placed.lines(), 0, 0, 0, false);
        placed.caret().ifPresent(caret -> fillBetween(caret.across(), caret.top(),
                caret.across() + caret.wide(), caret.top() + caret.high(), solidPaint(caret.colour())));
    }

    private void writeAll(List<PlacedText.PlacedLine> lines, int movedAcross, int movedDown,
            int everyRunIn, boolean isEveryRunOneColour) {

        for (PlacedText.PlacedLine line : lines) {
            for (PlacedText.PlacedPiece piece : line.pieces()) {
                Paint paint = convertToTextPaint(piece.font());
                paint.setColor(isEveryRunOneColour ? everyRunIn : convertToColour(piece.font().colour()));
                onto.drawText(piece.text(), (float) piece.across() + movedAcross,
                        (float) piece.baseline() + movedDown, paint);
            }
        }
    }

    private Paint convertToTextPaint(TextRun run) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(face);
        paint.setTextSize((float) run.size());
        paint.setFakeBoldText(run.bold());
        paint.setTextSkewX(run.italic() ? ITALIC_SLANT : 0);
        return paint;
    }

    private Paint solidPaint(Colour colour) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(convertToColour(colour));
        return paint;
    }

    private void fillBetween(double left, double top, double right, double bottom, Paint paint) {
        onto.drawRect(new RectF((float) left, (float) top,
                (float) Math.max(left, right), (float) Math.max(top, bottom)), paint);
    }

    private Shader convertToShader(Gradient gradient) {
        float[] stops = new float[gradient.stops().size()];
        int[] colours = new int[gradient.colours().size()];
        for (int at = 0; at < stops.length; at++) {
            stops[at] = (float) Math.min(1, Math.max(at / (float) stops.length, gradient.stops().get(at)));
            colours[at] = convertToColourAtFullOpacity(gradient.colours().get(at));
        }
        if (gradient.radial()) {
            return new RadialGradient((float) gradient.acrossOffset(), (float) gradient.downOffset(),
                    (float) Math.max(Float.MIN_NORMAL, gradient.measureRadius()), colours, stops,
                    Shader.TileMode.CLAMP);
        }
        return new LinearGradient((float) gradient.acrossStart(), (float) gradient.downStart(),
                (float) gradient.acrossEnd(), (float) gradient.downEnd(), colours, stops, Shader.TileMode.CLAMP);
    }

    private static Path buildPath(List<PathStep> steps, FillRule rule) {
        Path path = new Path();
        path.setFillType(rule == FillRule.EVEN_ODD ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
        for (PathStep step : steps) {
            addPathStep(path, step);
        }
        return path;
    }

    private static void addPathStep(Path path, PathStep step) {
        if (step instanceof PathStep.MoveTo to) {
            path.moveTo((float) to.across(), (float) to.down());
        } else if (step instanceof PathStep.LineTo to) {
            lineOrMoveTo(path, to);
        } else if (step instanceof PathStep.QuadraticTo to) {
            path.quadTo((float) to.controlAcross(), (float) to.controlDown(), (float) to.across(), (float) to.down());
        } else if (step instanceof PathStep.CubicTo to) {
            path.cubicTo((float) to.firstControlAcross(), (float) to.firstControlDown(),
                    (float) to.secondControlAcross(), (float) to.secondControlDown(),
                    (float) to.across(), (float) to.down());
        } else if (step instanceof PathStep.EllipseAt ellipse) {
            path.addOval(ellipseBounds(ellipse.centreAcross(), ellipse.centreDown(),
                    ellipse.radiusAcross(), ellipse.radiusDown()), Path.Direction.CW);
        } else if (step instanceof PathStep.ArcTo arc) {
            addArc(path, arc);
        } else if (step instanceof PathStep.Close) {
            path.close();
        }
    }

    private static void addArc(Path path, PathStep.ArcTo arc) {
        RectF bounds = ellipseBounds(arc.centreAcross(), arc.centreDown(), arc.radiusAcross(), arc.radiusDown());
        if (!arc.closes()) {
            path.addArc(bounds, (float) arc.beginsAt(), (float) arc.turnsThrough());
            return;
        }
        path.moveTo((float) arc.centreAcross(), (float) arc.centreDown());
        path.arcTo(bounds, (float) arc.beginsAt(), (float) arc.turnsThrough(), false);
        path.close();
    }

    private static RectF ellipseBounds(double centreAcross, double centreDown, double radiusAcross, double radiusDown) {
        return new RectF((float) (centreAcross - radiusAcross), (float) (centreDown - radiusDown),
                (float) (centreAcross + radiusAcross), (float) (centreDown + radiusDown));
    }

    private static void lineOrMoveTo(Path path, PathStep.LineTo to) {
        if (path.isEmpty()) {
            path.moveTo((float) to.across(), (float) to.down());
            return;
        }
        path.lineTo((float) to.across(), (float) to.down());
    }

    private static Matrix convertToMatrix(Transform transform) {
        Matrix matrix = new Matrix();
        matrix.setValues(new float[] {
                (float) transform.acrossScale(), (float) transform.acrossSkew(), (float) transform.acrossMove(),
                (float) transform.downSkew(), (float) transform.downScale(), (float) transform.downMove(),
                0, 0, 1});
        return matrix;
    }

    private int convertToColour(Colour colour) {
        return Color.argb(colour.opacity() * opacity / OPAQUE, colour.red(), colour.green(), colour.blue());
    }

    private static int convertToColourAtFullOpacity(Colour colour) {
        return Color.argb(colour.opacity(), colour.red(), colour.green(), colour.blue());
    }

    static Bitmap convertToBitmap(ImageValue pixels) {
        PairValue size = pixels.size();
        int wide = Math.max(1, (int) Math.round(size.x()));
        int high = Math.max(1, (int) Math.round(size.y()));
        int[] argb = new int[wide * high];
        for (int at = 0; at < argb.length; at++) {
            int[] parts = pixels.readPixelAt(at + 1);
            argb[at] = Color.argb(parts.length >= 4 ? parts[3] : OPAQUE, parts[0], parts[1], parts[2]);
        }
        return Bitmap.createBitmap(argb, wide, high, Bitmap.Config.ARGB_8888);
    }
}
