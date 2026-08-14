package org.jebol.domain.value;

import java.util.*;

/**
 * A thing that happened, waiting to be answered: a click, a key, a connection
 * opening, a file dropped on a window.
 *
 * <p>Not a series and not a container. The C keeps one in a single twelve-byte
 * value cell and says why: "events are kept compact in order to fit into normal
 * 128 bit values cells. This provides high performance for high frequency events
 * and also good memory efficiency using standard series." So an event has MAKE,
 * TO and nothing else -- {@code REBTYPE(Event)} sends every other action to
 * {@code Trap_Action} -- and every field a script reads is unpacked from those
 * bytes rather than stored as itself.
 *
 * <p>Held as a record here for the same reason a pair and a tuple are: the value
 * is the thing, there is no shared storage behind it, and writing a field through
 * a path makes a new event and puts it back where the old one came from.
 *
 * <p>Three things about the packing decide what a script sees.
 *
 * <p><b>The type is an index into {@code system/catalog/event-types}.</b> Which
 * makes that block the authority on what type words exist, and makes index 0 --
 * the word {@code ignore} -- unreadable: {@code Get_Event_Var} answers none for
 * it.
 *
 * <p><b>Offset, key and code are one four-byte field.</b> Two flags say how to
 * read it: HAS_XY for an offset packed as two signed shorts, HAS_CODE for a key
 * or a code. Writing either takes the other's flag down, so an event with a key
 * has no offset.
 *
 * <p><b>And port, gob and data share one slot</b>, with the model saying which is
 * in it. {@code window} and {@code gob} are two names for that one field.
 */
public record EventValue(
        int typeIndex,
        Set<EventValue.Flag> flags,
        EventValue.Model model,
        int data,
        Value attached) implements Value {

    /** The flags byte. {@code EVF_*} in reb-event.h. */
    public enum Flag {
        COPIED, HAS_XY, DOUBLE, CONTROL, SHIFT, HAS_DATA, HAS_CODE, ALT;

        /** The word {@code e/flags} answers for the three a script can read. */
        public String spelling() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * Which kind of thing the shared slot holds. {@code EVM_*} in reb-event.h.
     *
     * <p>Declared in this order because the C's enum is, and the order is what a
     * cleared event means: {@code CLEARS} leaves the model at zero, which is
     * DEVICE. So a fresh event claims to hold an I/O request and has none, which
     * is why its port reads as none.
     */
    public enum Model { DEVICE, PORT, OBJECT, GUI, CALLBACK, MIDI, CONSOLE }

    /**
     * The words {@code e/flags} answers, and the only three of the eight it does.
     *
     * <p>{@code if (VAL_EVENT_FLAGS(value) & (1<<EVF_DOUBLE | 1<<EVF_CONTROL |
     * 1<<EVF_SHIFT))} -- and nothing in {@code Set_Event_Var} writes any of them,
     * so a script reads them and a window system sets them.
     */
    public static final Set<Flag> READABLE_FLAGS =
            Set.of(Flag.DOUBLE, Flag.CONTROL, Flag.SHIFT);

    /** What each half of an offset fits in: a signed short. {@code Float_Int16}. */
    public static final int WIDEST_OFFSET_HALF = 0x7FFF;

    public EventValue {
        flags = Set.copyOf(flags);
    }

    /** What {@code CLEARS(out)} leaves: no type, no flags, model zero, no data. */
    public static EventValue fresh() {
        return new EventValue(0, Set.of(), Model.DEVICE, 0, NoneValue.none());
    }

    @Override
    public Datatype datatype() {
        return Datatype.EVENT;
    }

    public EventValue withType(int index) {
        return new EventValue(index, flags, model, data, attached);
    }

    public EventValue withModel(Model given) {
        return new EventValue(typeIndex, flags, given, data, attached);
    }

    public EventValue withAttached(Model given, Value held) {
        return new EventValue(typeIndex, flags, given, data, held);
    }

    /**
     * The data field written, with one reading flag raised and the other lowered.
     *
     * <p>Every arm that writes that field does both: {@code CLR_FLAG(...,
     * EVF_HAS_CODE); SET_FLAG(..., EVF_HAS_XY);} for an offset, and the reverse
     * for a key or a code. One field cannot be two things at once, and the flags
     * are how the C says which it currently is.
     */
    public EventValue withData(int given, Flag raised) {
        Set<Flag> now = EnumSet.noneOf(Flag.class);
        now.addAll(flags);
        now.remove(raised == Flag.HAS_XY ? Flag.HAS_CODE : Flag.HAS_XY);
        now.add(raised);
        return new EventValue(typeIndex, now, model, given, attached);
    }

    public boolean has(Flag flag) {
        return flags.contains(flag);
    }

    /** {@code VAL_EVENT_X}: the low half, read back through a signed short. */
    public int offsetX() {
        return (short) (data & 0xFFFF);
    }

    /** {@code VAL_EVENT_Y}: the high half, likewise. */
    public int offsetY() {
        return (short) ((data >> 16) & 0xFFFF);
    }

    /** {@code SET_EVENT_XY(v,x,y)}: {@code ((y << 16) | (x & 0xffff))}. */
    public static int packedOffset(int x, int y) {
        return (y << 16) | (x & 0xFFFF);
    }

    /**
     * The fields {@code Mold_Event} writes, as name and value pairs.
     *
     * <p>It walks a fixed list -- type, port, gob, offset, key, flags, code, data
     * -- and writes each one that answers something other than none. So the mold
     * shows what can be <em>read</em> rather than what was written: an event given
     * a key molds a code beside it, because the same bytes answer both.
     *
     * <p>The port is the one field this cannot always fill in. Three of the seven
     * models answer a field of {@code system/ports} rather than anything the event
     * holds, and those fields are none in a build with no window system -- in a
     * stock console 3.22.1 as much as here, which is why Rebol's own event test
     * guards its port case with {@code if system/ports/event [...]}. When a host
     * fills one of them, the resolution has to move to where the system object is
     * reachable.
     */
    public List<Value> moldingSpec() {
        List<Value> spec = new ArrayList<>();
        if (typeIndex != 0) {
            named(spec, "type", WordValue.of(EventCatalogue.typeAt(typeIndex)));
        }
        if (model == Model.PORT || model == Model.OBJECT || model == Model.MIDI) {
            named(spec, "port", attached);
        }
        if (model == Model.GUI && attached instanceof GobValue gob) {
            named(spec, "gob", gob);
        }
        if (has(Flag.HAS_XY)) {
            named(spec, "offset", PairValue.of(offsetX(), offsetY()));
        }
        keyRead().ifPresent(key -> named(spec, "key", key));
        if (!raisedFlagWords().isEmpty()) {
            named(spec, "flags", BlockValue.block(raisedFlagWords()));
        }
        if (has(Flag.HAS_CODE)) {
            named(spec, "code", IntegerValue.of(data));
        }
        return spec;
    }

    private static void named(List<Value> spec, String field, Value value) {
        spec.add(WordValue.of(field, Datatype.SET_WORD));
        spec.add(value);
    }

    /**
     * What {@code e/key} answers, or nothing.
     *
     * <p>One stored number read two ways, and the type decides which. A key or
     * key-up event reads it as a character; a control or control-up event reads it
     * as a position in {@code event-keys}. Every other type answers none, even
     * though the number is still there and {@code e/code} will hand it over.
     */
    public Optional<Value> keyRead() {
        if (typeIndex == EventCatalogue.KEY || typeIndex == EventCatalogue.KEY_UP) {
            return Optional.of(CharacterValue.of(data));
        }
        if (typeIndex == EventCatalogue.CONTROL || typeIndex == EventCatalogue.CONTROL_UP) {
            return data >= 1 && data <= EventCatalogue.KEYS.size()
                    ? Optional.of(WordValue.of(EventCatalogue.KEYS.get(data - 1)))
                    : Optional.empty();
        }
        return Optional.empty();
    }

    /** The three flag words a script can read, in the C's order. */
    public List<Value> raisedFlagWords() {
        List<Value> words = new ArrayList<>();
        for (Flag flag : List.of(Flag.DOUBLE, Flag.CONTROL, Flag.SHIFT)) {
            if (flags.contains(flag)) {
                words.add(WordValue.of(flag.spelling()));
            }
        }
        return words;
    }

    /**
     * Two events are equal when their model, type and data agree.
     *
     * <p>{@code Cmp_Event} compares exactly those three. Not the flags and not the
     * thing in the slot, so two events holding different ports are equal as long
     * as both hold one -- which is the C's decision rather than an omission: an
     * event is identified by what happened and where, and the port is who is being
     * told.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof EventValue event
                && event.model == model
                && event.typeIndex == typeIndex
                && event.data == data;
    }

    @Override
    public int hashCode() {
        return (model.ordinal() * 31 + typeIndex) * 31 + data;
    }

    @Override
    public String toString() {
        return Molder.mold(this);
    }
}
