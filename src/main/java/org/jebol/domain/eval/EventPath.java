package org.jebol.domain.eval;

import java.util.List;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.EventCatalogue;
import org.jebol.domain.value.EventValue;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

/**
 * An event's fields, from {@code Get_Event_Var} and {@code Set_Event_Var}.
 *
 * <p>An event is a value cell rather than a container, so every field here is
 * packed into or unpacked out of twelve bytes. Reading and writing are therefore
 * not symmetrical, and the places they part company are the interesting ones.
 *
 * <p>{@code port} takes a port, an object or none going in, and none means "this
 * belongs to the GUI" rather than "nothing". Coming out it answers whatever the
 * model names, which for three of the seven models is a field of
 * {@code system/ports} rather than anything the event holds.
 *
 * <p>{@code key} takes a character or a key word and puts a number in the data
 * field. Coming out, the <em>type</em> decides how that number is read: a
 * character for a key event, a word for a control event, and none for anything
 * else -- though {@code code} will hand over the number whatever the type.
 *
 * <p>{@code flags} can be read and not written: nothing in {@code Set_Event_Var}
 * touches them, because they say what a window system observed. {@code data} is
 * the same, and its two guards mean a script always reads none for it.
 */
final class EventPath {

    private EventPath() {
    }

    /**
     * What a field answers.
     *
     * <p>An unknown name is {@code PE_BAD_SELECT}, which is {@code invalid-path}
     * -- so a field the event has not got raises where an empty field it does have
     * answers none. {@code e/date} is an error and {@code e/data} is none.
     */
    static Value read(EventValue event, Value selector, Value guiPort,
            Value callbackPort, Value consolePort) {

        if (!(selector instanceof WordValue named)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "an event's fields are named, and "
                            + selector.datatype().literalSpelling() + " is not a name");
        }
        return switch (named.canonical()) {
            case "type" -> event.typeIndex() == 0
                    ? NoneValue.none()
                    : WordValue.of(EventCatalogue.typeAt(event.typeIndex()));
            case "port" -> portOf(event, guiPort, callbackPort, consolePort);
            // Two names for one field, sharing one arm in each direction.
            case "window", "gob" -> gobOf(event);
            case "offset" -> event.has(EventValue.Flag.HAS_XY)
                    ? PairValue.of(event.offsetX(), event.offsetY())
                    : NoneValue.none();
            case "key" -> event.keyRead().orElseGet(NoneValue::none);
            case "flags" -> event.raisedFlagWords().isEmpty()
                    ? NoneValue.none()
                    : BlockValue.block(event.raisedFlagWords());
            case "code" -> event.has(EventValue.Flag.HAS_CODE)
                    ? IntegerValue.of(event.data())
                    : NoneValue.none();
            case "data" -> droppedFileOf(event);
            default -> throw Raised.of(
                    EvaluationFailure.INVALID_PATH, named.spelling());
        };
    }

    /**
     * Who is being told about the event.
     *
     * <p>Seven models and five answers. A GUI event, a callback and a console
     * event each name a field of {@code system/ports}; a port event and a MIDI
     * event hold the port itself; an object event holds the object; and a device
     * event has to reach through an I/O request, which is the host's structure and
     * so answers none here.
     */
    private static Value portOf(EventValue event, Value guiPort,
            Value callbackPort, Value consolePort) {

        return switch (event.model()) {
            case GUI -> guiPort;
            case PORT, MIDI -> event.attached();
            case OBJECT -> event.attached();
            case CALLBACK -> callbackPort;
            case CONSOLE -> consolePort;
            // "assumes EVM_DEVICE ... Event holds the IO-Request, which has the
            // PORT" and `if (!req || !req->port) goto is_none`. A device request is
            // the host's own structure, and nothing here builds one.
            case DEVICE -> NoneValue.none();
        };
    }

    /**
     * The gob the event happened in.
     *
     * <p>Guarded three times: the model must be GUI, the HAS_DATA flag must be
     * down -- because a dropped file uses the same slot for a string -- and there
     * must be something in the slot.
     */
    private static Value gobOf(EventValue event) {
        if (event.model() != EventValue.Model.GUI
                || event.has(EventValue.Flag.HAS_DATA)
                || !(event.attached() instanceof GobValue gob)) {
            return NoneValue.none();
        }
        return gob;
    }

    /**
     * The file a drop-file event carried.
     *
     * <p>Two guards, and only the host can get past them: {@code if
     * (!GET_FLAG(..., EVF_HAS_DATA)) goto is_none;} and the type must be
     * {@code drop-file}. Nothing in {@code Set_Event_Var} raises HAS_DATA, so a
     * script always reads none -- which is the answer rather than a gap.
     */
    private static Value droppedFileOf(EventValue event) {
        if (!event.has(EventValue.Flag.HAS_DATA)
                || event.typeIndex() != EventCatalogue.DROP_FILE) {
            return NoneValue.none();
        }
        return event.attached();
    }

    /**
     * One field written, answering the event it leaves behind.
     *
     * <p>Empty where {@code Set_Event_Var} returns FALSE, which the caller turns
     * into {@code bad-field-set} when making an event and {@code bad-path-set}
     * when writing through a path. A word the type catalogue has not got is the
     * exception: {@code Trap_Arg(val)} raises from inside the setter, so it is
     * {@code invalid-arg} either way.
     */
    static java.util.Optional<EventValue> written(
            EventValue event, String field, Value value) {

        return switch (field) {
            case "type" -> writtenType(event, value);
            case "port" -> writtenPort(event, value);
            case "window", "gob" -> value instanceof GobValue gob
                    ? java.util.Optional.of(
                            event.withAttached(EventValue.Model.GUI, gob))
                    : java.util.Optional.empty();
            case "offset" -> writtenOffset(event, value);
            case "key" -> writtenKey(event, value);
            case "code" -> value instanceof IntegerValue given
                    ? java.util.Optional.of(event.withData(
                            (int) given.magnitude(), EventValue.Flag.HAS_CODE))
                    : java.util.Optional.empty();
            // FLAGS and DATA among them: `default: return FALSE`. Both can be read
            // and neither can be written, because both say what the host saw.
            default -> java.util.Optional.empty();
        };
    }

    /**
     * A type word, looked up in the catalogue.
     *
     * <p>Two refusals with two different errors, and the C means both. Something
     * that is not a word is {@code return FALSE}. A word the catalogue has not got
     * is {@code Trap_Arg(val)}, raised from inside the loop -- so it is
     * {@code invalid-arg} and not the caller's {@code bad-field-set}.
     */
    private static java.util.Optional<EventValue> writtenType(
            EventValue event, Value value) {

        if (!(value instanceof WordValue named)
                || !(named.datatype() == Datatype.WORD
                        || named.datatype() == Datatype.LIT_WORD)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(event.withType(
                EventCatalogue.typeIndexOf(named.canonical())
                        .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG,
                                named.spelling()
                                        + " is not in system/catalog/event-types"))));
    }

    /** A port, an object, or none meaning the GUI. */
    private static java.util.Optional<EventValue> writtenPort(
            EventValue event, Value value) {

        if (value instanceof PortValue port) {
            return java.util.Optional.of(
                    event.withAttached(EventValue.Model.PORT, port));
        }
        if (value instanceof ObjectValue object) {
            return java.util.Optional.of(
                    event.withAttached(EventValue.Model.OBJECT, object));
        }
        // `else if (IS_NONE(val)) { VAL_EVENT_MODEL(value) = EVM_GUI; }` -- and it
        // leaves the slot alone, so an event that held a gob keeps it.
        if (value instanceof NoneValue) {
            return java.util.Optional.of(event.withModel(EventValue.Model.GUI));
        }
        return java.util.Optional.empty();
    }

    /**
     * An offset, as two signed shorts.
     *
     * <p>{@code Float_Int16} raises {@code out-of-range} rather than truncating,
     * which is worth saying twice: a coordinate a window system could not have
     * produced is a mistake in the caller, and a truncated one would be a click in
     * the wrong place.
     */
    private static java.util.Optional<EventValue> writtenOffset(
            EventValue event, Value value) {

        if (!(value instanceof PairValue where)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(event.withData(
                EventValue.packedOffset(asShort(where.x()), asShort(where.y())),
                EventValue.Flag.HAS_XY));
    }

    /** `if (fabs(f) > (REBD32)(0x7FFF)) { ... Trap_Range(DS_TOP); }`. */
    private static int asShort(double half) {
        if (Math.abs(half) > EventValue.WIDEST_OFFSET_HALF) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "an event's offset holds -32767 to 32767, not " + (long) half);
        }
        return (int) half;
    }

    /**
     * A key, which also sets the model and may set the type.
     *
     * <p>{@code VAL_EVENT_MODEL(value) = EVM_GUI; if(!VAL_EVENT_TYPE(value))
     * VAL_EVENT_TYPE(value) = EVT_KEY;} run <em>before</em> the value is looked
     * at, so a key of the wrong datatype is refused after two fields have already
     * changed. Replicated as written, because a caller that catches the error and
     * carries on can see it.
     *
     * <p>A character is stored as its codepoint. A key word is stored as its
     * position plus one, shifted sixteen bits up -- which is what keeps a named key
     * from colliding with a character, since no character reaches that far down.
     */
    private static java.util.Optional<EventValue> writtenKey(
            EventValue event, Value value) {

        EventValue withTheModelSet = event.withModel(EventValue.Model.GUI);
        EventValue readied = withTheModelSet.typeIndex() == 0
                ? withTheModelSet.withType(EventCatalogue.KEY)
                : withTheModelSet;
        if (value instanceof CharacterValue letter) {
            return java.util.Optional.of(readied.withData(
                    letter.codepoint(), EventValue.Flag.HAS_CODE));
        }
        if (value instanceof WordValue named
                && (named.datatype() == Datatype.WORD
                        || named.datatype() == Datatype.LIT_WORD)) {
            java.util.Optional<Integer> at =
                    EventCatalogue.keyIndexOf(named.canonical());
            // `if (IS_END(arg)) return FALSE;` -- a word the key catalogue has not
            // got is a bad field set, where a *type* the type catalogue has not got
            // raises invalid-arg. The two loops fail differently and the C does not
            // tidy that up.
            return at.map(position -> readied.withData(
                    (position + 1) << 16, EventValue.Flag.HAS_CODE));
        }
        return java.util.Optional.empty();
    }

    /**
     * The spec block an event is made from: {@code Set_Event_Vars}.
     *
     * <p>Walked in pairs, and a set-word with nothing after it is read as none
     * rather than refused -- {@code if (IS_END(val)) val = NONE_VALUE;} -- which is
     * the opposite of what a gob does with the same shape. So
     * {@code make event! [port:]} works and {@code make event! [type:]} fails,
     * because none is a legal port and not a legal type.
     */
    static EventValue filledFromSpec(EventValue start, List<Value> spec,
            java.util.function.UnaryOperator<Value> simpleValueOf) {

        EventValue built = start;
        for (int at = 0; at < spec.size(); at += 2) {
            Value name = spec.get(at);
            Value given = at + 1 < spec.size() ? spec.get(at + 1) : NoneValue.none();
            // `if (IS_END(val)) val = NONE_VALUE; else val = Get_Simple_Value(val);`
            // -- so a word or a path in a spec block becomes what it holds, and a
            // set-word with nothing after it becomes none.
            Value written = given.datatype() == Datatype.UNSET
                    ? NoneValue.none()
                    : simpleValueOf.apply(given);
            // Every name is passed to the setter, whatever its datatype: unlike a
            // gob, an event does not check for a set-word first, so a spec block of
            // plain words fails as a field the event has not got.
            String field = name instanceof WordValue named ? named.canonical() : "";
            java.util.Optional<EventValue> after = written(built, field, written);
            if (after.isEmpty()) {
                throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                        name instanceof WordValue named
                                ? WordValue.of(named.spelling())
                                : name,
                        DatatypeValue.of(written.datatype()));
            }
            built = after.orElseThrow();
        }
        return built;
    }

    /**
     * MAKE EVENT! and TO EVENT!, which are one arm.
     *
     * <p>{@code if (action == A_MAKE || action == A_TO)`, and then three cases. An
     * event alone answers that very event. Anything with a block starts from a
     * <em>cleared</em> event and fills it -- so {@code make some-event [offset:
     * 0x0]} is not a copy of that event with an offset, which is the one thing
     * about this the name gets wrong. Everything else is
     * {@code Trap_Types(RE_EXPECT_VAL, REB_EVENT, ...)}.
     */
    static Value made(Value from, Value spec,
            java.util.function.UnaryOperator<Value> simpleValueOf) {
        if (spec instanceof EventValue already) {
            return already;
        }
        if (!(spec instanceof BlockValue block) || block.datatype() != Datatype.BLOCK) {
            throw Raised.of(EvaluationFailure.EXPECT_VAL,
                    DatatypeValue.of(Datatype.EVENT),
                    DatatypeValue.of(spec.datatype()));
        }
        // `from` is either the datatype or an event, and neither contributes a
        // field: `CLEARS(&(D_RET->data.event))` runs for both.
        if (!(from instanceof EventValue) && !(from instanceof DatatypeValue)) {
            throw Raised.of(EvaluationFailure.EXPECT_VAL,
                    DatatypeValue.of(Datatype.EVENT),
                    DatatypeValue.of(from.datatype()));
        }
        return filledFromSpec(EventValue.fresh(), block.remaining(), simpleValueOf);
    }

}
