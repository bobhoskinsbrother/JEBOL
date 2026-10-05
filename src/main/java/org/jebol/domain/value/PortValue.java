package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;

public record PortValue(Context context) implements Value {

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

    public void setField(String name, Value replacement) {
        context.set(name, replacement);
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

    public void refuseAnActorThatIsNeitherAWordNorAnObject() {
        Value actor = fieldValue("actor");
        if (actor instanceof ObjectValue || actor instanceof WordValue
                || actor instanceof NoneValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.INVALID_ACTOR);
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
