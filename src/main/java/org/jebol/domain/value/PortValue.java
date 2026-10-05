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
        if (!(fieldNamed("scheme") instanceof ObjectValue(Context context1))) {
            return "";
        }
        return context1.holds("name")
                && context1.ownSlotFor("name").value() instanceof WordValue word
                ? word.canonical()
                : "";
    }

    public Value fieldNamed(String name) {
        return context.holds(name)
                ? context.ownSlotFor(name).value()
                : NoneValue.none();
    }

    public void setField(String name, Value replacement) {
        context.set(name, replacement);
    }

    public boolean isOpen() {
        Value whatTheActorLeft = fieldNamed("state");
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
