package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record PortValue(Context context) implements Value {

    private static final Set<String> THE_SCHEMES_THAT_ARE_QUEUES =
            Set.of("system", "event", "callback");

    public PortValue {
        if (context == null || context.isUnbound()) {
            throw new IllegalArgumentException("a port needs a real context");
        }
    }

    @Override
    public Optional<Context> fieldsAsAContext() {
        return Optional.of(context);
    }

    @Override
    public List<Value> items() {
        return context.boundWordsAndValues();
    }

    public String schemeName() {
        return fieldValue("scheme").fieldValue("name") instanceof WordValue word
                ? word.canonical()
                : "";
    }

    public Optional<BlockValue> eventQueue() {
        if (!THE_SCHEMES_THAT_ARE_QUEUES.contains(schemeName())) {
            return Optional.empty();
        }
        if (!(fieldValue("state") instanceof BlockValue queue)) {
            BlockValue made = BlockValue.block(List.of());
            setField("state", made);
            return Optional.of(made);
        }
        return Optional.of(queue);
    }

    public Value queued(Value happening, boolean atTheEnd) {
        if (!(happening instanceof EventValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, happening);
        }
        BlockValue queue = eventQueue().orElseThrow();
        queue.storage().insertAt(
                atTheEnd ? queue.storage().length() + 1 : queue.index(), happening);
        return this;
    }

    public PortValue withItsQueueEmptied() {
        eventQueue().ifPresent(queue -> {
            while (queue.storage().length() > 0) {
                queue.storage().removeAt(1);
            }
        });
        return this;
    }

    public boolean isAFile() {
        return schemeName().equals("file") || schemeName().equals("dir");
    }

    public void setField(String name, Value replacement) {
        context.register(name, replacement);
    }

    public Optional<ObjectValue> actorWrittenInRebol() {
        return fieldValue("actor") instanceof ObjectValue actor
                ? Optional.of(actor)
                : Optional.empty();
    }

    public void refuseASpecThatIsNotAnObject() {
        if (!(fieldValue("spec") instanceof ObjectValue)) {
            throw Raised.of(EvaluationFailure.INVALID_PORT);
        }
    }

    public void refuseAnActorThatIsNeitherANativeNorAnObject() {
        Value actor = fieldValue("actor");
        if (actor instanceof ObjectValue || actor instanceof NativeValue
                || actor instanceof NoneValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.INVALID_ACTOR);
    }

    public boolean hasNoActor() {
        return fieldValue("actor") instanceof NoneValue;
    }

    public boolean isOpen() {
        Value whatTheActorLeft = fieldValue("state");
        return !(whatTheActorLeft instanceof ObjectValue)
                && whatTheActorLeft.isTruthy();
    }

    public void markOpen(boolean open) {
        setField("state", LogicValue.of(open));
    }

    @Override
    public Datatype datatype() {
        return Datatype.PORT;
    }

    @Override
    public String toString() {
        String scheme = schemeName();
        return scheme.isEmpty() ? "port" : "port on " + scheme;
    }
}
