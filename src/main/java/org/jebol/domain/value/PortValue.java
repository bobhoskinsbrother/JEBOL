package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record PortValue(Context context) implements Value, PathTargetWithFields {

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
    public List<Value> listTheItems() {
        return context.listTheBoundWordsAndValues();
    }

    public String schemeName() {
        return readFieldValue("scheme").readFieldValue("name") instanceof AnyWordValue word
                ? word.canonical()
                : "";
    }

    public Optional<AnyBlockValue> findTheEventQueue() {
        if (!THE_SCHEMES_THAT_ARE_QUEUES.contains(schemeName())) {
            return Optional.empty();
        }
        if (!(readFieldValue("state") instanceof AnyBlockValue queue)) {
            AnyBlockValue made = BlockValue.of(List.of());
            setField("state", made);
            return Optional.of(made);
        }
        return Optional.of(queue);
    }

    public Value queue(Value happening, boolean atTheEnd) {
        if (!(happening instanceof EventValue)) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, happening);
        }
        AnyBlockValue queue = findTheEventQueue().orElseThrow();
        queue.storage().insertAt(
                atTheEnd ? queue.storage().length() + 1 : queue.index(), happening);
        return this;
    }

    public PortValue withItsQueueEmptied() {
        findTheEventQueue().ifPresent(queue -> {
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
        return readFieldValue("actor") instanceof ObjectValue actor
                ? Optional.of(actor)
                : Optional.empty();
    }

    public void refuseASpecThatIsNotAnObject() {
        if (!(readFieldValue("spec") instanceof ObjectValue)) {
            throw Raised.of(EvaluationFailure.INVALID_PORT);
        }
    }

    public void refuseAnActorThatIsNeitherANativeNorAnObject() {
        Value actor = readFieldValue("actor");
        if (actor instanceof ObjectValue || actor instanceof NativeValue
                || actor instanceof NoneValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.INVALID_ACTOR);
    }

    public boolean hasNoActor() {
        return readFieldValue("actor") instanceof NoneValue;
    }

    public boolean isOpen() {
        Value whatTheActorLeft = readFieldValue("state");
        return !(whatTheActorLeft instanceof ObjectValue)
                && whatTheActorLeft.isTruthy();
    }

    public void markOpen(boolean open) {
        setField("state", LogicValue.of(open));
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new PortDatatype();

    private static final class PortDatatype extends AnyObjectDatatype {

        PortDatatype() {
            super("port");
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            throw refuseConstruction(contents);
        }

        @Override
        public Value makeFrom(Value spec, Maker maker) {
            if (!canNameScheme(spec)) {
                throw Raised.of(EvaluationFailure.INVALID_SPEC, spec);
            }
            if (!(maker.applySystemFunction("make-port*", spec) instanceof PortValue port)) {
                throw Raised.of(EvaluationFailure.INVALID_SPEC, spec);
            }
            return port;
        }

        private boolean canNameScheme(Value spec) {
            return spec instanceof FileValue
                    || spec instanceof UrlValue
                    || spec instanceof BlockValue
                    || spec instanceof ObjectValue
                    || spec instanceof WordValue
                    || spec instanceof PortValue;
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            if (!(from instanceof ObjectValue(Context fields))) {
                throw refuse(from);
            }
            return new PortValue(fields);
        }
    }

    @Override
    public String toString() {
        String scheme = schemeName();
        return scheme.isEmpty() ? "port" : "port on " + scheme;
    }
}
