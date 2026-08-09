package org.jebol.domain.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.StringValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

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

    /** The face kinds, and the HTML element each becomes. */
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

    /** The colour words REBOL defines, as the demo sources use them. */
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
     * The same, looking words up as it goes.
     *
     * <p>A layout is not evaluated -- evaluating it would call the face kinds
     * as though they were functions -- but a word in it still means what it
     * names, so {@code button caption} shows whatever caption holds. Words
     * that name a face kind or a colour are the dialect's own and are not
     * looked up.
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
            decorate(current, resolved(item, lookUp));
        }
        return List.copyOf(faces);
    }

    /** A word that is not the dialect's own stands for whatever it names. */
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

    /**
     * Attaches one value to the face it followed. What a value means is
     * decided by its datatype, which is what lets a layout be written without
     * naming any of these properties.
     */
    private static void decorate(Face face, Value item) {
        switch (item) {
            case StringValue text -> face.setCaption(text.text());
            case PairValue size -> {
                face.style("width", size.x() + "px");
                face.style("height", size.y() + "px");
            }
            case TupleValue colour -> face.style("background-color", asRgb(colour));
            case WordValue word -> colourNamed(word.canonical())
                    .ifPresent(rgb -> face.style("background-color", rgb));
            case IntegerValue ignored -> {
                // A bare number in a layout is a position or a rate, neither
                // of which a browser lays out. Dropped rather than guessed at.
            }
            case CharacterValue character -> face.setCaption(character.toString());
            case BlockValue block -> {
                // The first block after a face is what happens when it is
                // acted on. Later ones are effect and feel, which describe a
                // desktop toolkit and have nothing to draw here.
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
        return "rgb(" + colour.segmentAt(1) + "," + colour.segmentAt(2)
                + "," + colour.segmentAt(3) + ")";
    }
}
