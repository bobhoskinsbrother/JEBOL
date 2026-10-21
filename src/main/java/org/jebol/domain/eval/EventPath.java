package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.List;
import java.util.function.UnaryOperator;

public final class EventPath {

    private EventPath() {
    }

    static Value read(EventValue event, Value selector, Value guiPort,
            Value callbackPort, Value consolePort) {

        if (!(selector instanceof AnyWordValue asked)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "an event's fields are named, and "
                            + selector.datatype().literalSpelling() + " is not a name");
        }
        return switch (asked.canonical()) {
            case "type" -> event.typeIndex() == 0
                    ? NoneValue.none()
                    : WordValue.of(EventCatalogue.readTypeAt(event.typeIndex()));
            case "port" -> findThePortOf(event, guiPort, callbackPort, consolePort);
            case "window", "gob" -> attachedGob(event);
            case "offset" -> event.has(EventValue.Flag.HAS_XY)
                    ? PairValue.of(event.offsetX(), event.offsetY())
                    : NoneValue.none();
            case "key" -> event.readKey().orElseGet(NoneValue::none);
            case "flags" -> event.listTheRaisedFlagWords().isEmpty()
                    ? NoneValue.none()
                    : BlockValue.of(event.listTheRaisedFlagWords());
            case "code" -> event.has(EventValue.Flag.HAS_CODE)
                    ? IntegerValue.of(event.data())
                    : NoneValue.none();
            case "data" -> readTheDroppedFileOf(event);
            default -> throw Raised.of(
                    EvaluationFailure.INVALID_PATH, asked.spelling());
        };
    }

    private static Value findThePortOf(EventValue event, Value guiPort,
                                       Value callbackPort, Value consolePort) {

        return switch (event.model()) {
            case GUI -> guiPort;
            case PORT, MIDI -> event.attached();
            case OBJECT -> event.attached();
            case CALLBACK -> callbackPort;
            case CONSOLE -> consolePort;
            case DEVICE -> refuseIoRequest();
        };
    }

    private static Value refuseIoRequest() {
        return NoneValue.none();
    }

    private static Value attachedGob(EventValue event) {
        if (event.model() != EventValue.Model.GUI
                || isADroppedFile(event)
                || !(event.attached() instanceof GobValue gob)) {
            return NoneValue.none();
        }
        return gob;
    }

    private static boolean isADroppedFile(EventValue event) {
        return event.has(EventValue.Flag.HAS_DATA);
    }

    private static Value readTheDroppedFileOf(EventValue event) {
        if (!event.has(EventValue.Flag.HAS_DATA)
                || event.typeIndex() != EventCatalogue.DROP_FILE) {
            return NoneValue.none();
        }
        return event.attached();
    }

    static java.util.Optional<EventValue> write(
            EventValue event, String field, Value value) {

        return switch (field) {
            case "type" -> writeTheType(event, value);
            case "port" -> writeThePort(event, value);
            case "window", "gob" -> value instanceof GobValue gob
                    ? java.util.Optional.of(
                            event.withAttached(EventValue.Model.GUI, gob))
                    : java.util.Optional.empty();
            case "offset" -> writeTheOffset(event, value);
            case "key" -> writeTheKey(event, value);
            case "code" -> value instanceof IntegerValue(long magnitude)
                    ? java.util.Optional.of(event.withData(
                            (int) magnitude, EventValue.Flag.HAS_CODE))
                    : java.util.Optional.empty();
            default -> java.util.Optional.empty();
        };
    }

    private static java.util.Optional<EventValue> writeTheType(
            EventValue event, Value value) {

        if (!(value instanceof AnyWordValue eventType)
                || !(eventType instanceof WordValue || eventType instanceof LitWordValue)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(event.withType(
                EventCatalogue.findTheTypeIndexOf(eventType.canonical())
                        .orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_ARG,
                                eventType.spelling()
                                        + " is not in system/catalog/event-types"))));
    }

    private static java.util.Optional<EventValue> writeThePort(
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

    private static java.util.Optional<EventValue> writeTheOffset(
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

    private static java.util.Optional<EventValue> writeTheKey(
            EventValue event, Value value) {

        EventValue theModelAndTypeChangeBeforeTheValueIsLookedAt =
                withModelAndTypeSetFirst(event);
        if (value instanceof CharacterValue(int codepoint)) {
            return java.util.Optional.of(
                    theModelAndTypeChangeBeforeTheValueIsLookedAt.withData(
                            codepoint, EventValue.Flag.HAS_CODE));
        }
        if (value instanceof AnyWordValue keyWord
                && (keyWord instanceof WordValue || keyWord instanceof LitWordValue)) {
            java.util.Optional<Integer> at =
                    EventCatalogue.findTheKeyIndexOf(keyWord.canonical());
            return at.map(position ->
                    theModelAndTypeChangeBeforeTheValueIsLookedAt.withData(
                            keepNamedKeyApartFromCharacters(position),
                            EventValue.Flag.HAS_CODE));
        }
        return java.util.Optional.empty();
    }

    private static EventValue withModelAndTypeSetFirst(EventValue event) {
        EventValue withTheModelSet = event.withModel(EventValue.Model.GUI);
        return withTheModelSet.typeIndex() == 0
                ? withTheModelSet.withType(EventCatalogue.KEY)
                : withTheModelSet;
    }

    private static int keepNamedKeyApartFromCharacters(int position) {
        return (position + 1) << 16;
    }

    static EventValue fillFromSpec(EventValue start, List<Value> spec,
                                   java.util.function.UnaryOperator<Value> simpleValueOf) {

        EventValue built = start;
        for (int at = 0; at < spec.size(); at += 2) {
            Value name = spec.get(at);
            Value given = readTheValueAfterASetWord(spec, at);
            Value written = given.datatype() == UnsetValue.TYPE
                    ? NoneValue.none()
                    : simpleValueOf.apply(given);
            String field = name instanceof AnyWordValue asked ? asked.canonical() : "";
            java.util.Optional<EventValue> after = write(built, field, written);
            if (after.isEmpty()) {
                throw Raised.of(EvaluationFailure.BAD_FIELD_SET,
                        name, written.datatype());
            }
            built = after.orElseThrow();
        }
        return built;
    }

    private static Value readTheValueAfterASetWord(
            List<Value> spec, int at) {
        return at + 1 < spec.size() ? spec.get(at + 1) : NoneValue.none();
    }

    public static Value make(Value spec, UnaryOperator<Value> simpleValueOf) {
        if (spec instanceof EventValue already) {
            return already;
        }
        if (!(spec instanceof BlockValue block)) {
            throw Raised.of(EvaluationFailure.EXPECT_VAL, EventValue.TYPE, spec.datatype());
        }
        return fillFromSpec(
                makeClearedEvent(), block.remaining(), simpleValueOf);
    }

    private static EventValue makeClearedEvent() {
        return EventValue.fresh();
    }

}
