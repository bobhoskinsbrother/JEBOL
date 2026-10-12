package org.jebol.adapter.web;

import org.jebol.domain.render.ClipRectangle;
import org.jebol.domain.render.Colour;
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
import org.jebol.domain.render.SurfaceCall;
import org.jebol.domain.render.TextRun;
import org.jebol.domain.render.Transform;
import org.jebol.domain.value.ImageValue;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class SurfaceCallsAsJson {

    private SurfaceCallsAsJson() {
    }

    static String written(List<SurfaceCall> calls, int wide, int high) {
        String written = calls.stream().map(SurfaceCallsAsJson::asAnObject)
                .collect(Collectors.joining(",", "[", "]"));
        return anObject(
                counting("wide", wide),
                counting("high", high),
                holding("paint", written));
    }

    private static String asAnObject(SurfaceCall call) {
        return switch (call) {
            case SurfaceCall.Filled filled -> anObject(
                    saying("kind", "fill"),
                    placed(filled.where()),
                    holding("transform", asSixNumbers(filled.transform())),
                    holding("path", asSteps(filled.path())),
                    saying("rule", filled.rule().spelling()),
                    filled.gradient()
                            .map(gradient -> holding("gradient", asAGradient(gradient)))
                            .orElseGet(() -> saying("colour", filled.colour().asCss())),
                    holding("smooth", String.valueOf(filled.antiAliased())));
            case SurfaceCall.Stroked stroked -> anObject(
                    saying("kind", "stroke"),
                    placed(stroked.where()),
                    holding("transform", asSixNumbers(stroked.transform())),
                    holding("path", asSteps(stroked.path())),
                    asAStroke(stroked.painted()));
            case SurfaceCall.Pictured shown -> anObject(
                    saying("kind", "picture"),
                    placed(shown.where()),
                    holding("transform", asSixNumbers(shown.transform())),
                    measuring("draw-wide", shown.wide()),
                    measuring("draw-high", shown.high()),
                    counting("pixel-wide", (int) Math.round(shown.image().size().x())),
                    counting("pixel-high", (int) Math.round(shown.image().size().y())),
                    saying("pixels", asOctets(shown.image())));
            case SurfaceCall.Written written -> anObject(
                    saying("kind", "text"),
                    placed(written.where()),
                    saying("selection-colour",
                            PlacedText.THE_COLOUR_A_SELECTION_IS_MARKED_IN.asCss()),
                    holding("lines", asLines(written.text().lines())),
                    holding("selection", asSelection(written.text().selection())),
                    holding("caret", asACaret(written.text().caret())),
                    holding("shadow", asAShadow(written.text().shadow())));
        };
    }

    private static String asLines(List<PlacedLine> lines) {
        return lines.stream().map(line -> anObject(
                holding("pieces", line.pieces().stream()
                        .map(SurfaceCallsAsJson::asAPiece)
                        .collect(Collectors.joining(",", "[", "]")))))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String asAPiece(PlacedPiece piece) {
        TextRun font = piece.font();
        return anObject(
                measuring("across", piece.across()),
                measuring("baseline", piece.baseline()),
                saying("text", piece.text()),
                saying("colour", font.colour().asCss()),
                measuring("size", font.size()),
                holding("bold", String.valueOf(font.bold())),
                holding("italic", String.valueOf(font.italic())));
    }

    private static String asSelection(List<PlacedPatch> patches) {
        return patches.stream().map(SurfaceCallsAsJson::asAPatch)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String asAPatch(PlacedPatch patch) {
        return anObject(
                measuring("across", patch.across()),
                measuring("top", patch.top()),
                measuring("wide", patch.wide()),
                measuring("high", patch.high()));
    }

    private static String asACaret(Optional<PlacedCaret> caret) {
        return caret.map(placed -> anObject(
                measuring("across", placed.across()),
                measuring("top", placed.top()),
                measuring("wide", placed.wide()),
                measuring("high", placed.high()),
                saying("colour", placed.colour().asCss())))
                .orElse("null");
    }

    private static String asAShadow(Optional<PlacedShadow> shadow) {
        return shadow.map(placed -> anObject(
                counting("across", placed.across()),
                counting("down", placed.down()),
                saying("colour", placed.colour().asCss())))
                .orElse("null");
    }

    private static String asSixNumbers(Transform transform) {
        return "[" + String.join(",",
                measuring(transform.acrossScale()), measuring(transform.downSkew()),
                measuring(transform.acrossSkew()), measuring(transform.downScale()),
                measuring(transform.acrossMove()), measuring(transform.downMove()))
                + "]";
    }

    private static String asSteps(List<PathStep> path) {
        return path.stream().map(SurfaceCallsAsJson::asAStep)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String asAStep(PathStep step) {
        String kind = saying("step", step.kind().spelling());
        return switch (step) {
            case PathStep.MoveTo to -> anObject(kind, at(to.across(), to.down()));
            case PathStep.LineTo to -> anObject(kind, at(to.across(), to.down()));
            case PathStep.QuadraticTo to -> anObject(kind,
                    at(to.across(), to.down()),
                    controlling(to.controlAcross(), to.controlDown()));
            case PathStep.CubicTo to -> anObject(kind,
                    at(to.across(), to.down()),
                    controlling(to.firstControlAcross(), to.firstControlDown()),
                    secondControl(to.secondControlAcross(), to.secondControlDown()));
            case PathStep.EllipseAt ellipse -> anObject(kind,
                    at(ellipse.centreAcross(), ellipse.centreDown()),
                    radiating(ellipse.radiusAcross(), ellipse.radiusDown()));
            case PathStep.ArcTo arc -> anObject(kind,
                    at(arc.centreAcross(), arc.centreDown()),
                    radiating(arc.radiusAcross(), arc.radiusDown()),
                    measuring("begins", arc.beginsAt()),
                    measuring("turns", arc.turnsThrough()),
                    holding("closes", String.valueOf(arc.closes())));
            case PathStep.Close ignored -> anObject(kind);
        };
    }

    private static String at(double across, double down) {
        return measuring("across", across) + "," + measuring("down", down);
    }

    private static String controlling(double across, double down) {
        return measuring("control-across", across) + ","
                + measuring("control-down", down);
    }

    private static String secondControl(double across, double down) {
        return measuring("second-across", across) + ","
                + measuring("second-down", down);
    }

    private static String radiating(double across, double down) {
        return measuring("radius-across", across) + ","
                + measuring("radius-down", down);
    }

    private static String asAStroke(PaintState painted) {
        return saying("colour", painted.strokeColour().orElseThrow().asCss()) + ","
                + measuring("width", painted.lineWidth()) + ","
                + saying("cap", painted.lineCap().spelling()) + ","
                + saying("join", painted.lineJoin().spelling()) + ","
                + saying("rule", painted.fillRule().spelling()) + ","
                + holding("dashes", asAListOfNumbers(painted.dashes())) + ","
                + holding("smooth", String.valueOf(painted.antiAliased()));
    }

    private static String asAGradient(Gradient gradient) {
        StringBuilder stops = new StringBuilder("[");
        for (int at = 0; at < gradient.colours().size(); at++) {
            stops.append(at == 0 ? "" : ",").append(anObject(
                    measuring("at", Math.min(1, Math.max(0, gradient.stops().get(at)))),
                    saying("colour", gradient.colours().get(at).asCss())));
        }
        return anObject(
                saying("gradient", gradient.radial() ? "radial" : "linear"),
                measuring("across-start", gradient.acrossStart()),
                measuring("down-start", gradient.downStart()),
                measuring("across-end", gradient.acrossEnd()),
                measuring("down-end", gradient.downEnd()),
                measuring("radius", gradient.radius()),
                holding("stops", stops.append("]").toString()));
    }

    private static String asAListOfNumbers(List<Double> numbers) {
        StringBuilder written = new StringBuilder("[");
        for (int at = 0; at < numbers.size(); at++) {
            written.append(at == 0 ? "" : ",").append(measuring(numbers.get(at)));
        }
        return written.append("]").toString();
    }

    private static String placed(Placement where) {
        return holding("clip", asAnObject(where.clip())) + ","
                + holding("clip-shape", asSteps(where.clipShape())) + ","
                + counting("across", where.across()) + ","
                + counting("down", where.down()) + ","
                + counting("wide", where.wide()) + ","
                + counting("high", where.high()) + ","
                + counting("opacity", where.opacity());
    }

    private static String asAnObject(ClipRectangle clip) {
        return anObject(
                counting("across", clip.across()),
                counting("down", clip.down()),
                counting("wide", clip.wide()),
                counting("high", clip.high()));
    }

    private static String anObject(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    private static String saying(String name, String value) {
        return asAString(name) + ":" + asAString(value);
    }

    private static String counting(String name, int value) {
        return asAString(name) + ":" + value;
    }

    private static String measuring(String name, double value) {
        return asAString(name) + ":" + measuring(value);
    }

    private static String measuring(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private static String holding(String name, String alreadyWritten) {
        return asAString(name) + ":" + alreadyWritten;
    }

    static String asAString(String text) {
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
            int[] parts = pixels.pixelAt(pixel + 1);
            octets[pixel * CHANNELS_A_PIXEL] = (byte) parts[0];
            octets[pixel * CHANNELS_A_PIXEL + 1] = (byte) parts[1];
            octets[pixel * CHANNELS_A_PIXEL + 2] = (byte) parts[2];
            octets[pixel * CHANNELS_A_PIXEL + 3] = (byte) (parts.length >= 4 ? parts[3] : 255);
        }
        return Base64.getEncoder().encodeToString(octets);
    }

    private static final int CHANNELS_A_PIXEL = 4;
}
