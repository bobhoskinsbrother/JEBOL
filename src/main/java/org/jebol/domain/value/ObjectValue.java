package org.jebol.domain.value;

/** An object: a context reached as a value. */
public record ObjectValue(Context context) implements Value {

    public ObjectValue {
        if (context == null || context.isUnbound()) {
            throw new IllegalArgumentException("an object needs a real context");
        }
    }

    /**
     * Two objects are equal when they hold the same fields with equal
     * values, whatever contexts they were built in.
     *
     * <p>The record default compares the context by identity, so two
     * objects built the same way were never equal. Nothing noticed until
     * construction syntax made it easy to build the same object twice.
     *
     * <p>{@code self} is left out, as it is left out of molding: every
     * object has one and it points back at the object, so counting it
     * would make this recurse for ever.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ObjectValue object
                && context.fieldsExcludingSelf().equals(object.context.fieldsExcludingSelf())
                // Two objects with the same visible fields are still
                // different if one is hiding something. A hidden field
                // has no name and no value to compare -- but it is there,
                // and an object with one is not an empty object.
                && context.fieldCount() == object.context.fieldCount();
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
