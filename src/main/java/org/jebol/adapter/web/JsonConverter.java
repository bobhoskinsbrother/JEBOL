package org.jebol.adapter.web;

import org.jebol.domain.render.ClipRectangle;
import org.jebol.domain.render.Gradient;
import org.jebol.domain.render.PathStep;
import org.jebol.domain.render.PaintState;
import org.jebol.domain.render.Placement;
import org.jebol.domain.render.PlacedText;
import org.jebol.domain.render.PlacedText.PlacedCaret;
import org.jebol.domain.render.PlacedText.PlacedLine;
import org.jebol.domain.render.PlacedText.PlacedPatch;
import org.jebol.domain.render.PlacedText.PlacedPiece;
import org.jebol.domain.render.PlacedText.PlacedShadow;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.render.TextRun;
import org.jebol.domain.render.Transform;
import org.jebol.domain.value.ImageValue;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class JsonConverter {

    private JsonConverter() {
    }

    static String write(List<DrawingCommand> calls, int width, int height) {
        String written = calls.stream().map(JsonConverter::asObject)
                .collect(Collectors.joining(",", "[", "]"));
        return writeObject(
                writeWholeNumberField("wide", width),
                writeWholeNumberField("high", height),
                writeNestedField("paint", written));
    }

    private static String asObject(DrawingCommand call) {
        return switch (call) {
            case DrawingCommand.Fill fill -> writeObject(
                    writeTextField("type", "fill"),
                    writePlacement(fill.where()),
                    writeNestedField("transform", asSixNumbers(fill.transform())),
                    writeNestedField("path", asSteps(fill.path())),
                    writeTextField("rule", fill.rule().spelling()),
                    fill.gradient()
                            .map(gradient -> writeNestedField("gradient", asGradient(gradient)))
                            .orElseGet(() -> writeTextField("colour", fill.colour().asCss())),
                    writeNestedField("smooth", String.valueOf(fill.antiAliased())));
            case DrawingCommand.Stroke stroke -> writeObject(
                    writeTextField("type", "stroke"),
                    writePlacement(stroke.where()),
                    writeNestedField("transform", asSixNumbers(stroke.transform())),
                    writeNestedField("path", asSteps(stroke.path())),
                    asStroke(stroke.painted()));
            case DrawingCommand.Picture shown -> writeObject(
                    writeTextField("type", "picture"),
                    writePlacement(shown.where()),
                    writeNestedField("transform", asSixNumbers(shown.transform())),
                    writeNumberField("draw-wide", shown.wide()),
                    writeNumberField("draw-high", shown.high()),
                    writeWholeNumberField("pixel-wide", (int) Math.round(shown.image().size().x())),
                    writeWholeNumberField("pixel-high", (int) Math.round(shown.image().size().y())),
                    writeTextField("pixels", asOctets(shown.image())));
            case DrawingCommand.Text text -> writeObject(
                    writeTextField("type", "text"),
                    writePlacement(text.where()),
                    writeTextField("selection-colour",
                            PlacedText.THE_COLOUR_A_SELECTION_IS_MARKED_IN.asCss()),
                    writeNestedField("lines", asLines(text.text().lines())),
                    writeNestedField("selection", asSelection(text.text().selection())),
                    writeNestedField("caret", asCaret(text.text().caret())),
                    writeNestedField("shadow", asShadow(text.text().shadow())));
        };
    }

    private static String asLines(List<PlacedLine> lines) {
        return lines.stream().map(line -> writeObject(
                writeNestedField("pieces", line.pieces().stream()
                        .map(JsonConverter::asPiece)
                        .collect(Collectors.joining(",", "[", "]")))))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String asPiece(PlacedPiece piece) {
        TextRun font = piece.font();
        return writeObject(
                writeNumberField("across", piece.across()),
                writeNumberField("baseline", piece.baseline()),
                writeTextField("text", piece.text()),
                writeTextField("colour", font.colour().asCss()),
                writeNumberField("size", font.size()),
                writeNestedField("bold", String.valueOf(font.bold())),
                writeNestedField("italic", String.valueOf(font.italic())));
    }

    private static String asSelection(List<PlacedPatch> patches) {
        return patches.stream().map(JsonConverter::asPatch)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String asPatch(PlacedPatch patch) {
        return writeObject(
                writeNumberField("across", patch.across()),
                writeNumberField("top", patch.top()),
                writeNumberField("wide", patch.wide()),
                writeNumberField("high", patch.high()));
    }

    private static String asCaret(Optional<PlacedCaret> caret) {
        return caret.map(placed -> writeObject(
                writeNumberField("across", placed.across()),
                writeNumberField("top", placed.top()),
                writeNumberField("wide", placed.wide()),
                writeNumberField("high", placed.high()),
                writeTextField("colour", placed.colour().asCss())))
                .orElse("null");
    }

    private static String asShadow(Optional<PlacedShadow> shadow) {
        return shadow.map(placed -> writeObject(
                writeWholeNumberField("across", placed.across()),
                writeWholeNumberField("down", placed.down()),
                writeTextField("colour", placed.colour().asCss())))
                .orElse("null");
    }

    private static String asSixNumbers(Transform transform) {
        return "[" + String.join(",",
                writeNumber(transform.acrossScale()), writeNumber(transform.downSkew()),
                writeNumber(transform.acrossSkew()), writeNumber(transform.downScale()),
                writeNumber(transform.acrossMove()), writeNumber(transform.downMove()))
                + "]";
    }

    private static String asSteps(List<PathStep> path) {
        return path.stream().map(JsonConverter::asStep)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String asStep(PathStep step) {
        String kind = writeTextField("step", step.kind().spelling());
        return switch (step) {
            case PathStep.MoveTo to -> writeObject(kind, at(to.across(), to.down()));
            case PathStep.LineTo to -> writeObject(kind, at(to.across(), to.down()));
            case PathStep.QuadraticTo to -> writeObject(kind,
                    at(to.across(), to.down()),
                    writeControlPoint(to.controlAcross(), to.controlDown()));
            case PathStep.CubicTo to -> writeObject(kind,
                    at(to.across(), to.down()),
                    writeControlPoint(to.firstControlAcross(), to.firstControlDown()),
                    writeSecondControlPoint(to.secondControlAcross(), to.secondControlDown()));
            case PathStep.EllipseAt ellipse -> writeObject(kind,
                    at(ellipse.centreAcross(), ellipse.centreDown()),
                    writeRadius(ellipse.radiusAcross(), ellipse.radiusDown()));
            case PathStep.ArcTo arc -> writeObject(kind,
                    at(arc.centreAcross(), arc.centreDown()),
                    writeRadius(arc.radiusAcross(), arc.radiusDown()),
                    writeNumberField("begins", arc.beginsAt()),
                    writeNumberField("turns", arc.turnsThrough()),
                    writeNestedField("closes", String.valueOf(arc.closes())));
            case PathStep.Close ignored -> writeObject(kind);
        };
    }

    private static String at(double across, double down) {
        return writeNumberField("across", across) + "," + writeNumberField("down", down);
    }

    private static String writeControlPoint(double across, double down) {
        return writeNumberField("control-across", across) + ","
                + writeNumberField("control-down", down);
    }

    private static String writeSecondControlPoint(double across, double down) {
        return writeNumberField("second-across", across) + ","
                + writeNumberField("second-down", down);
    }

    private static String writeRadius(double across, double down) {
        return writeNumberField("radius-across", across) + ","
                + writeNumberField("radius-down", down);
    }

    private static String asStroke(PaintState painted) {
        return writeTextField("colour", painted.strokeColour().orElseThrow().asCss()) + ","
                + writeNumberField("width", painted.lineWidth()) + ","
                + writeTextField("cap", painted.lineCap().spelling()) + ","
                + writeTextField("join", painted.lineJoin().spelling()) + ","
                + writeTextField("rule", painted.fillRule().spelling()) + ","
                + writeNestedField("dashes", asListOfNumbers(painted.dashes())) + ","
                + writeNestedField("smooth", String.valueOf(painted.isAntiAlias()));
    }

    private static String asGradient(Gradient gradient) {
        StringBuilder stops = new StringBuilder("[");
        for (int at = 0; at < gradient.colours().size(); at++) {
            stops.append(at == 0 ? "" : ",").append(writeObject(
                    writeNumberField("at", Math.min(1, Math.max(0, gradient.stops().get(at)))),
                    writeTextField("colour", gradient.colours().get(at).asCss())));
        }
        return writeObject(
                writeTextField("gradient", gradient.radial() ? "radial" : "linear"),
                writeNumberField("across-start", gradient.acrossStart()),
                writeNumberField("down-start", gradient.downStart()),
                writeNumberField("across-end", gradient.acrossEnd()),
                writeNumberField("down-end", gradient.downEnd()),
                writeNumberField("radius", gradient.measureTheRadius()),
                writeNestedField("stops", stops.append("]").toString()));
    }

    private static String asListOfNumbers(List<Double> numbers) {
        StringBuilder written = new StringBuilder("[");
        for (int at = 0; at < numbers.size(); at++) {
            written.append(at == 0 ? "" : ",").append(writeNumber(numbers.get(at)));
        }
        return written.append("]").toString();
    }

    private static String writePlacement(Placement where) {
        return writeNestedField("clip", asObject(where.clip())) + ","
                + writeNestedField("clip-shape", asSteps(where.clipShape())) + ","
                + writeWholeNumberField("across", where.across()) + ","
                + writeWholeNumberField("down", where.down()) + ","
                + writeWholeNumberField("wide", where.wide()) + ","
                + writeWholeNumberField("high", where.high()) + ","
                + writeWholeNumberField("opacity", where.opacity());
    }

    private static String asObject(ClipRectangle clip) {
        return writeObject(
                writeWholeNumberField("across", clip.across()),
                writeWholeNumberField("down", clip.down()),
                writeWholeNumberField("wide", clip.wide()),
                writeWholeNumberField("high", clip.high()));
    }

    private static String writeObject(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    private static String writeTextField(String name, String value) {
        return asString(name) + ":" + asString(value);
    }

    private static String writeWholeNumberField(String name, int value) {
        return asString(name) + ":" + value;
    }

    private static String writeNumberField(String name, double value) {
        return asString(name) + ":" + writeNumber(value);
    }

    private static String writeNumber(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private static String writeNestedField(String name, String alreadyWritten) {
        return asString(name) + ":" + alreadyWritten;
    }

    static String asString(String text) {
        StringBuilder written = new StringBuilder("\"");
        for (int at = 0; at < text.length(); at++) {
            char here = text.charAt(at);
            switch (here) {
                case '"' -> written.append("\\\"");
                case '\\' -> written.append("\\\\");
                case '\n' -> written.append("\\n");
                case '\r' -> written.append("\\r");
                case '\t' -> written.append("\\t");
                default -> {
                    if (here < 0x20) {
                        written.append(String.format("\\u%04x", (int) here));
                    } else {
                        written.append(here);
                    }
                }
            }
        }
        return written.append("\"").toString();
    }

    private static String asOctets(ImageValue pixels) {
        int wide = (int) Math.round(pixels.size().x());
        int high = (int) Math.round(pixels.size().y());
        byte[] octets = new byte[wide * high * CHANNELS_A_PIXEL];
        for (int pixel = 0; pixel < wide * high; pixel++) {
            int[] parts = pixels.readPixelAt(pixel + 1);
            octets[pixel * CHANNELS_A_PIXEL] = (byte) parts[0];
            octets[pixel * CHANNELS_A_PIXEL + 1] = (byte) parts[1];
            octets[pixel * CHANNELS_A_PIXEL + 2] = (byte) parts[2];
            octets[pixel * CHANNELS_A_PIXEL + 3] = (byte) (parts.length >= 4 ? parts[3] : 255);
        }
        return Base64.getEncoder().encodeToString(octets);
    }

    private static final int CHANNELS_A_PIXEL = 4;
}
