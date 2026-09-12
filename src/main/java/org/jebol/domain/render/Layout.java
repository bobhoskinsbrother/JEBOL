package org.jebol.domain.render;

import org.jebol.domain.value.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a VID layout block into the faces it describes.
 *
 * <p>The dialect is positional: a word names a kind of face, and everything
 * after it decorates that face until the next kind word. A string is its
 * caption, a pair is its size, a tuple or a colour word is its colour. That
 * is the whole grammar, and it is why VID reads as a description rather than
 * as a sequence of instructions.
 *
 * <p>Words this does not recognise are ignored rather than refused. A layout
 * written for View mentions things a browser has no counterpart for --
 * {@code rate}, {@code feel}, {@code effect} -- and dropping them renders
 * something useful instead of nothing.
 */
public final class Layout {

    private static final Map<String, String> KINDS = Map.ofEntries(
            Map.entry("text", "p"),
            Map.entry("label", "label"),
            Map.entry("title", "h1"),
            Map.entry("h1", "h1"),
            Map.entry("h2", "h2"),
            Map.entry("h3", "h3"),
            Map.entry("banner", "h2"),
            Map.entry("button", "button"),
            Map.entry("btn", "button"),
            Map.entry("field", "input"),
            Map.entry("area", "textarea"),
            Map.entry("box", "div"),
            Map.entry("panel", "div"),
            Map.entry("image", "img"),
            Map.entry("slider", "input"),
            Map.entry("check", "input"),
            Map.entry("toggle", "button"));

    private static final Map<String, int[]> COLOURS = Map.ofEntries(
            Map.entry("black", new int[] {0, 0, 0}),
            Map.entry("white", new int[] {255, 255, 255}),
            Map.entry("red", new int[] {255, 0, 0}),
            Map.entry("green", new int[] {0, 255, 0}),
            Map.entry("blue", new int[] {0, 0, 255}),
            Map.entry("yellow", new int[] {255, 255, 0}),
            Map.entry("cyan", new int[] {0, 255, 255}),
            Map.entry("magenta", new int[] {255, 0, 255}),
            Map.entry("gray", new int[] {128, 128, 128}),
            Map.entry("grey", new int[] {128, 128, 128}),
            Map.entry("orange", new int[] {255, 150, 10}),
            Map.entry("brown", new int[] {139, 69, 19}),
            Map.entry("pink", new int[] {255, 192, 203}),
            Map.entry("purple", new int[] {128, 0, 128}),
            Map.entry("navy", new int[] {0, 0, 128}),
            Map.entry("teal", new int[] {0, 128, 128}),
            Map.entry("silver", new int[] {192, 192, 192}),
            Map.entry("gold", new int[] {255, 215, 0}),
            Map.entry("snow", new int[] {255, 250, 250}),
            Map.entry("ivory", new int[] {255, 255, 240}));

    private Layout() {
    }

    /** Whether a word names a kind of face. */
    public static boolean isFaceKind(String canonical) {
        return KINDS.containsKey(canonical);
    }

    /** The HTML element a face kind becomes. */
    public static String elementFor(String kind) {
        return KINDS.getOrDefault(kind, "div");
    }

    /** The faces a layout block describes, in the order it describes them. */
    public static List<Face> facesIn(BlockValue layout) {
        return facesIn(layout, word -> java.util.Optional.empty());
    }

    /**
     * The same, looking words up as it goes: a layout is not evaluated, but a
     * word in it still means what it names, except where it is the dialect's
     * own.
     */
    public static List<Face> facesIn(
            BlockValue layout, java.util.function.Function<String, java.util.Optional<Value>> lookUp) {

        List<Face> faces = new ArrayList<>();
        Face current = null;

        for (Value item : layout.remaining()) {
            if (item instanceof WordValue word && isFaceKind(word.canonical())) {
                current = new Face(word.canonical());
                faces.add(current);
                continue;
            }
            if (current == null) {
                continue;
            }
            decorateByDatatypeSoNothingHasToBeNamed(current, resolved(item, lookUp));
        }
        return List.copyOf(faces);
    }

    private static Value resolved(
            Value item,
            java.util.function.Function<String, java.util.Optional<Value>> lookUp) {

        if (!(item instanceof WordValue word) || word.datatype() != org.jebol.domain.value.Datatype.WORD) {
            return item;
        }
        if (COLOURS.containsKey(word.canonical())) {
            return item;
        }
        return lookUp.apply(word.canonical()).orElse(item);
    }

    private static void decorateByDatatypeSoNothingHasToBeNamed(Face face, Value item) {
        switch (item) {
            case StringValue text -> face.setCaption(text.text());
            case PairValue size -> {
                face.style("width", Molder.moldHalf(size.x()) + "px");
                face.style("height", Molder.moldHalf(size.y()) + "px");
            }
            case TupleValue colour -> face.style("background-color", asRgb(colour));
            case WordValue word -> colourNamed(word.canonical())
                    .ifPresent(rgb -> face.style("background-color", rgb));
            case IntegerValue ignored -> {
            }
            case CharacterValue character -> face.setCaption(character.toString());
            case BlockValue block -> {
                if (face.action().isEmpty()) {
                    face.setAction(block);
                }
            }
            default -> face.setCaption(Molder.form(item));
        }
    }

    private static java.util.Optional<String> colourNamed(String canonical) {
        int[] rgb = COLOURS.get(canonical.toLowerCase(Locale.ROOT));
        return rgb == null
                ? java.util.Optional.empty()
                : java.util.Optional.of("rgb(" + rgb[0] + "," + rgb[1] + "," + rgb[2] + ")");
    }

    private static String asRgb(TupleValue colour) {
        return "rgb(" + colour.octetAt(1) + "," + colour.octetAt(2)
                + "," + colour.octetAt(3) + ")";
    }
}
