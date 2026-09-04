package org.jebol.adapter.web;

import org.jebol.domain.render.*;
import org.jebol.domain.value.ImageValue;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A paint list on the wire.
 *
 * <p>The one place where "every renderer gets the same list" turns into bytes,
 * so the one place the claim can quietly break. Every number a renderer would
 * otherwise have had to work out has to survive the crossing: the position,
 * the clip and the opacity all travel, and a browser that had to deduce any of
 * them would be deciding something.
 *
 * <p>Written by hand because the project has no runtime dependencies and this
 * is a few dozen lines of numbers and strings. The punctuation is named --
 * {@code saying}, {@code counting}, {@code holding} -- rather than escaped
 * inline, because a page of {@code "\"kind\":\""} is the kind of thing nobody
 * reads and everybody assumes is right.
 *
 * <p>Everything a script supplied is escaped. A caption is data, and data
 * arriving from a script must not become code, or the first person to put a
 * quotation mark in one has added an instruction to the list.
 *
 * <p>An image crosses as its own pixels rather than as an encoded picture,
 * which keeps this free of {@code java.awt} -- worth having, because the same
 * adapter would then serve from a runtime that has no AWT at all.
 */
final class PaintListAsJson {

    private PaintListAsJson() {
    }

    /** A whole message: how big the surface is, and what to paint on it. */
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

    /** A field whose value is a number that may have a fraction. */
    private static String measuring(String name, double value) {
        return asAString(name) + ":" + measuring(value);
    }

    private static String measuring(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    /** Everything every kind carries: what it is, where it goes, what it may cover. */
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

    /** A field whose value is text a reader must not mistake for anything else. */
    private static String saying(String name, String value) {
        return asAString(name) + ":" + asAString(value);
    }

    /** A field whose value is a whole number. */
    private static String counting(String name, int value) {
        return asAString(name) + ":" + value;
    }

    /** A field whose value is already written out. */
    private static String holding(String name, String alreadyWritten) {
        return asAString(name) + ":" + alreadyWritten;
    }

    /**
     * An image as red, green, blue and opacity for every pixel, in reading
     * order, base sixty-four.
     *
     * <p>Which is what a browser's own image data wants, so the page builds
     * one from these bytes and puts it straight on the canvas with no decoding
     * of a picture format at either end.
     */
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

    /**
     * A string as JSON, with everything that could end it escaped.
     *
     * <p>The shortest piece of this file and the one that matters most. A
     * caption goes on the wire exactly as a script wrote it and comes out the
     * other end as one string, whatever it contains.
     */
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
