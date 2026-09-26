package org.jebol.domain.value;

/** An object: a context reached as a value. */
public record ObjectValue(Context context) implements Value {

    public ObjectValue {
        if (context == null || context.isUnbound()) {
            throw new IllegalArgumentException("an object needs a real context");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ObjectValue(Context context1)
                && context.fieldsExcludingSelf().equals(context1.fieldsExcludingSelf())
                && context.fieldCount() == context1.fieldCount();
    }

    @Override
    public int hashCode() {
        return context.fieldsExcludingSelf().hashCode();
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
