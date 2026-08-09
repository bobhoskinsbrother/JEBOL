package org.jebol.domain.value;

/** An object: a context reached as a value. */
public record ObjectValue(Context context) implements Value {

    public ObjectValue {
        if (context == null || context.isUnbound()) {
            throw new IllegalArgumentException("an object needs a real context");
        }
    }

    @Override
    public Datatype datatype() {
        return Datatype.OBJECT;
    }

    @Override
    public String toString() {
        return "object with " + context.slotCount() + " fields";
    }
}
