package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;

/**
 * The two lists an event's type and its named keys are positions in.
 *
 * <p>Both live in {@code boot/sysobj.reb} as {@code system/catalog/event-types}
 * and {@code system/catalog/event-keys}, and both carry the same warning above
 * them: "Order dependent for C and REBOL. Due to fixed C constants, this list
 * cannot be reordered after release!" The C generates {@code reb-evtypes.h} from
 * the first one, so the position of a word in it <em>is</em> the constant.
 *
 * <p>Kept here rather than written into the system object directly, because two
 * different things read them. A script reads the blocks in
 * {@code system/catalog}, and the event datatype reads the same order to turn a
 * word into a number and back. Building the blocks from this list is what stops
 * the two drifting apart -- the same reason the named character sets are computed
 * from one place rather than written out twice.
 */
public final class EventCatalogue {

    private EventCatalogue() {
    }

    /**
     * {@code system/catalog/event-types}, in order.
     *
     * <p>The first is {@code ignore} at index 0, and index 0 is what a cleared
     * event holds -- which is why {@code Get_Event_Var} answers none for it and
     * the word {@code ignore} can be written and never read back.
     */
    public static final List<String> TYPES = List.of(
            "ignore", "interrupt", "device", "callback", "custom", "error", "init",
            "open", "close", "connect", "accept", "read", "write", "wrote", "lookup",
            "ready", "done", "time",
            "show", "hide", "offset", "resize", "active", "inactive",
            "minimize", "maximize", "restore",
            "move", "down", "up", "alt-down", "alt-up", "aux-down", "aux-up",
            "key", "key-up",
            "scroll-line", "scroll-page",
            "drop-file",
            "click", "change", "focus", "unfocus", "scroll",
            "control", "control-up",
            "char");

    /**
     * {@code system/catalog/event-keys}, in order.
     *
     * <p>A key given as a word is stored as its position plus one, shifted into
     * the top half of the data field: {@code VAL_EVENT_DATA(value) = (n+1) << 16}.
     * The shift is what keeps a named key from colliding with a character, and the
     * plus one is what keeps the first key from being indistinguishable from no
     * key at all.
     */
    public static final List<String> KEYS = List.of(
            "page-up", "page-down", "end", "home", "left", "up", "right", "down",
            "insert", "delete",
            "f1", "f2", "f3", "f4", "f5", "f6", "f7", "f8", "f9", "f10", "f11", "f12",
            "paste-start", "paste-end", "escape",
            "shift", "control", "alt", "pause", "capital",
            "backtab", "backspace", "begin");

    /** The four type words whose events read a key back, and how. */
    public static final int KEY = TYPES.indexOf("key");
    public static final int KEY_UP = TYPES.indexOf("key-up");
    public static final int CONTROL = TYPES.indexOf("control");
    public static final int CONTROL_UP = TYPES.indexOf("control-up");
    public static final int DROP_FILE = TYPES.indexOf("drop-file");

    /** Where a type word sits, or nothing when the list has not got it. */
    public static Optional<Integer> typeIndexOf(String word) {
        int at = TYPES.indexOf(word);
        return at < 0 ? Optional.empty() : Optional.of(at);
    }

    /** Where a key word sits, or nothing. */
    public static Optional<Integer> keyIndexOf(String word) {
        int at = KEYS.indexOf(word);
        return at < 0 ? Optional.empty() : Optional.of(at);
    }

    /** The word at a type index, for reading one back. */
    public static String typeAt(int index) {
        return TYPES.get(index);
    }

    /** The blocks {@code system/catalog} publishes, built from the lists above. */
    public static BlockValue typesBlock() {
        return blockOfWords(TYPES);
    }

    public static BlockValue keysBlock() {
        return blockOfWords(KEYS);
    }

    private static BlockValue blockOfWords(List<String> words) {
        return BlockValue.block(words.stream().<Value>map(WordValue::of).toList());
    }
}
