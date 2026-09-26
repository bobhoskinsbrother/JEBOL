package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.List;

final class EventPath {

    private EventPath() {
    }

    static Value read(EventValue event, Value selector, Value guiPort,
            Value callbackPort, Value consolePort) {

        if (!(selector instanceof WordValue asked)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "an event's fields are named, and "
                            + selector.datatype().literalSpelling() + " is not a name");
        }
        return switch (asked.canonical()) {
            case "type" -> event.typeIndex() == 0
                    ? NoneValue.none()
                    : WordValue.of(EventCatalogue.typeAt(event.typeIndex()));
            case "port" -> portOf(event, guiPort, callbackPort, consolePort);
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
                    EvaluationFailure.INVALID_PATH, asked.spelling());
        };
    }

    private static Value portOf(EventValue event, Value guiPort,
            Value callbackPort, Value consolePort) {

        return switch (event.model()) {
            case GUI -> guiPort;
            case PORT, MIDI -> event.attached();
            case OBJECT -> event.attached();
            case CALLBACK -> callbackPort;
            case CONSOLE -> consolePort;
            case DEVICE -> onlyTheHostCanReachAnIoRequest();
        };
    }

    private static Value onlyTheHostCanReachAnIoRequest() {
        return NoneValue.none();
    }

    private static Value gobOf(EventValue event) {
        if (event.model() != EventValue.Model.GUI
                || aDroppedFileUsesTheSameSlotForAString(event)
                || !(event.attached() instanceof GobValue gob)) {
            return NoneValue.none();
        }
        return gob;
    }

    private static boolean aDroppedFileUsesTheSameSlotForAString(EventValue event) {
        return event.has(EventValue.Flag.HAS_DATA);
    }

    private static Value droppedFileOf(EventValue event) {
        if (!event.has(EventValue.Flag.HAS_DATA)
                || event.typeIndex() != EventCatalogue.DROP_FILE) {
            return NoneValue.none();
        }
        return event.attached();
    }

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
            case "code" -> value instanceof IntegerValue(long magnitude)
                    ? java.util.Optional.of(event.withData(
                            (int) magnitude, EventValue.Flag.HAS_CODE))
                    : java.util.Optional.empty();
            default -> java.util.Optional.empty();
        };
    }

    private static java.util.Optional<EventValue> writtenType(
            EventValue event, Value value) {

        if (!(value instanceof WordValue eventType)
                || !(eventType.datatype() == Datatype.WORD
                        || eventType.datatype() == Datatype.LIT_WORD)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(event.withType(
                EventCatalogue.typeIndexOf(eventType.canonical())
                        .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG,
                                eventType.spelling()
                                        + " is not in system/catalog/event-types"))));
    }

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
        if (value instanceof NoneValue) {
            return java.util.Optional.of(event.withModel(EventValue.Model.GUI));
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<EventValue> writtenOffset(
            EventValue event, Value value) {

        if (!(value instanceof PairValue(double x, double y))) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(event.withData(
                EventValue.packedOffset(
                        asShortRaisingRatherThanTruncating(x),
                        asShortRaisingRatherThanTruncating(y)),
                EventValue.Flag.HAS_XY));
    }

    private static int asShortRaisingRatherThanTruncating(double half) {
        if (Math.abs(half) > EventValue.WIDEST_OFFSET_HALF) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "an event's offset holds -32767 to 32767, not " + (long) half);
        }
        return (int) half;
    }

    private static java.util.Optional<EventValue> writtenKey(
            EventValue event, Value value) {

        EventValue theModelAndTypeChangeBeforeTheValueIsLookedAt =
                withTheModelAndTypeSetFirst(event);
        if (value instanceof CharacterValue(int codepoint)) {
            return java.util.Optional.of(
                    theModelAndTypeChangeBeforeTheValueIsLookedAt.withData(
                            codepoint, EventValue.Flag.HAS_CODE));
        }
        if (value instanceof WordValue keyWord
                && (keyWord.datatype() == Datatype.WORD
                        || keyWord.datatype() == Datatype.LIT_WORD)) {
            java.util.Optional<Integer> at =
                    EventCatalogue.keyIndexOf(keyWord.canonical());
            return at.map(position ->
                    theModelAndTypeChangeBeforeTheValueIsLookedAt.withData(
                            aNamedKeyCannotCollideWithACharacter(position),
                            EventValue.Flag.HAS_CODE));
        }
        return java.util.Optional.empty();
    }

    private static EventValue withTheModelAndTypeSetFirst(EventValue event) {
        EventValue withTheModelSet = event.withModel(EventValue.Model.GUI);
        return withTheModelSet.typeIndex() == 0
                ? withTheModelSet.withType(EventCatalogue.KEY)
                : withTheModelSet;
    }

    private static int aNamedKeyCannotCollideWithACharacter(int position) {
        return (position + 1) << 16;
    }

    static EventValue filledFromSpec(EventValue start, List<Value> spec,
            java.util.function.UnaryOperator<Value> simpleValueOf) {

        EventValue built = start;
        for (int at = 0; at < spec.size(); at += 2) {
            Value name = spec.get(at);
            Value given = aSetWordWithNothingAfterItReadsAsNone(spec, at);
            Value written = given.datatype() == Datatype.UNSET
                    ? NoneValue.none()
                    : simpleValueOf.apply(given);
            String field = name instanceof WordValue asked ? asked.canonical() : "";
            java.util.Optional<EventValue> after = written(built, field, written);
            if (after.isEmpty()) {
                throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                        name instanceof WordValue asked
                                ? WordValue.of(asked.spelling())
                                : name,
                        DatatypeValue.of(written.datatype()));
            }
            built = after.orElseThrow();
        }
        return built;
    }

    private static Value aSetWordWithNothingAfterItReadsAsNone(
            List<Value> spec, int at) {
        return at + 1 < spec.size() ? spec.get(at + 1) : NoneValue.none();
    }

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
        if (!(from instanceof EventValue) && !(from instanceof DatatypeValue)) {
            throw Raised.of(EvaluationFailure.EXPECT_VAL,
                    DatatypeValue.of(Datatype.EVENT),
                    DatatypeValue.of(from.datatype()));
        }
        return filledFromSpec(
                aBlockStartsFromAClearedEvent(), block.remaining(), simpleValueOf);
    }

    private static EventValue aBlockStartsFromAClearedEvent() {
        return EventValue.fresh();
    }

}
