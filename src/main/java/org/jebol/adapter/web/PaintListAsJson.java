package org.jebol.adapter.web;

import org.jebol.domain.render.*;
import org.jebol.domain.value.ImageValue;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

final class PaintListAsJson {

    private PaintListAsJson() {
    }

    static String written(PaintList painting, int wide, int high) {
        String instructions = painting.instructions().stream()
                .map(PaintListAsJson::asAnObject)
                .collect(Collectors.joining(",", "[", "]"));
        return anObject(
                counting("wide", wide),
                counting("high", high),
                holding("paint", instructions));
    }

    private static String asAnObject(PaintInstruction instruction) {
        return switch (instruction) {
            case PaintInstruction.Fill filled -> anObject(
                    placed(filled),
                    saying("colour", filled.colour().asHexTriplet()));
            case PaintInstruction.Writing written -> anObject(
                    placed(written),
                    saying("text", written.text()),
                    saying("colour", written.colour().asHexTriplet()));
            case PaintInstruction.Picture shown -> anObject(
                    placed(shown),
                    saying("pixels", asOctets(shown.pixels())));
            case PaintInstruction.Drawn drawing -> anObject(
                    placed(drawing),
                    holding("transform", asSixNumbers(drawing.transform())),
                    holding("path", asSteps(drawing.path())),
                    holding("stroke", asAStroke(drawing.painted())),
                    holding("fill", asAFill(drawing.painted())),
                    holding("smooth", String.valueOf(drawing.painted().antiAliased())));
        };
    }

    private static String asSixNumbers(Transform transform) {
        return "[" + String.join(",",
                measuring(transform.acrossScale()), measuring(transform.downSkew()),
                measuring(transform.acrossSkew()), measuring(transform.downScale()),
                measuring(transform.acrossMove()), measuring(transform.downMove()))
                + "]";
    }

    private static String asSteps(List<PathStep> path) {
        return path.stream().map(PaintListAsJson::asAStep)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String asAStep(PathStep step) {
        String named = saying("step", step.kind().spelling());
        return switch (step) {
            case PathStep.MoveTo to -> anObject(named, at(to.across(), to.down()));
            case PathStep.LineTo to -> anObject(named, at(to.across(), to.down()));
            case PathStep.QuadraticTo to -> anObject(named,
                    at(to.across(), to.down()),
                    controlling(to.controlAcross(), to.controlDown()));
            case PathStep.CubicTo to -> anObject(named,
                    at(to.across(), to.down()),
                    controlling(to.firstControlAcross(), to.firstControlDown()),
                    secondControl(to.secondControlAcross(), to.secondControlDown()));
            case PathStep.EllipseAt ellipse -> anObject(named,
                    at(ellipse.centreAcross(), ellipse.centreDown()),
                    radiating(ellipse.radiusAcross(), ellipse.radiusDown()));
            case PathStep.ArcTo arc -> anObject(named,
                    at(arc.centreAcross(), arc.centreDown()),
                    radiating(arc.radiusAcross(), arc.radiusDown()),
                    measuring("begins", arc.beginsAt()),
                    measuring("turns", arc.turnsThrough()),
                    holding("closes", String.valueOf(arc.closes())));
            case PathStep.Close ignored -> anObject(named);
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
        return painted.strokeColour()
                .map(colour -> anObject(
                        saying("colour", colour.asHexTriplet()),
                        measuring("width", painted.lineWidth()),
                        saying("cap", painted.lineCap().spelling()),
                        saying("join", painted.lineJoin().spelling())))
                .orElse("null");
    }

    private static String asAFill(PaintState painted) {
        return painted.fillColour()
                .map(colour -> anObject(
                        saying("colour", colour.asHexTriplet()),
                        saying("rule", painted.fillRule().spelling())))
                .orElse("null");
    }

    private static String measuring(String name, double value) {
        return asAString(name) + ":" + measuring(value);
    }

    private static String measuring(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private static String placed(PaintInstruction instruction) {
        Placement where = instruction.where();
        return String.join(",",
                saying("kind", instruction.kind().spelling()),
                counting("across", where.across()),
                counting("down", where.down()),
                counting("wide", where.wide()),
                counting("high", where.high()),
                counting("opacity", where.opacity()),
                holding("clip", asAnObject(where.clip())));
    }

    private static String asAnObject(ClipRectangle clip) {
        return anObject(
                counting("across", clip.across()),
                counting("down", clip.down()),
                counting("wide", clip.wide()),
                counting("high", clip.high()));
    }

    private static String anObject(String... fields) {
        return Arrays.stream(fields).collect(Collectors.joining(",", "{", "}"));
    }

    private static String saying(String name, String value) {
        return asAString(name) + ":" + asAString(value);
    }

    private static String counting(String name, int value) {
        return asAString(name) + ":" + value;
    }

    private static String holding(String name, String alreadyWritten) {
        return asAString(name) + ":" + alreadyWritten;
    }

    private static String asOctets(ImageValue pixels) {
        int wide = (int) Math.round(pixels.size().x());
        int high = (int) Math.round(pixels.size().y());
        byte[] octets = new byte[Math.max(0, wide * high * CHANNELS_A_PIXEL)];
        for (int pixel = 0; pixel < wide * high; pixel++) {
            int[] parts = pixels.pixelAt(pixel);
            int at = pixel * CHANNELS_A_PIXEL;
            octets[at] = (byte) parts[0];
            octets[at + 1] = (byte) parts[1];
            octets[at + 2] = (byte) parts[2];
            octets[at + 3] = (byte) (parts.length >= 4 ? parts[3] : OPAQUE);
        }
        return Base64.getEncoder().encodeToString(octets);
    }

    private static final int CHANNELS_A_PIXEL = 4;
    private static final int OPAQUE = 255;

    static String asAString(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        text.codePoints().forEach(character -> quoted.append(escaped(character)));
        return quoted.append('"').toString();
    }

    private static String escaped(int character) {
        return switch (character) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> character < ' '
                    ? String.format("\\u%04x", character)
                    : new String(Character.toChars(character));
        };
    }
}
