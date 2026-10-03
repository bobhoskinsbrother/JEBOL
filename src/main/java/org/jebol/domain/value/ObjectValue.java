package org.jebol.domain.value;

import java.util.Optional;

/** An object: a context reached as a value. */
public record ObjectValue(Context context) implements Value {

    @Override
    public boolean equalTo(Value other, Sameness how) {
        return other instanceof ObjectValue theirs
                && everyFieldAgrees(theirs, (ours, yours) ->
                        ours.equalTo(yours, Sameness.insideASeries()));
    }

    public boolean everyFieldAgrees(
            ObjectValue other, java.util.function.BiPredicate<Value, Value> agree) {

        java.util.Map<String, Value> ours = context.fieldsExcludingSelf();
        java.util.Map<String, Value> theirs = other.context.fieldsExcludingSelf();
        if (!ours.keySet().equals(theirs.keySet())
                || context.fieldCount() != other.context.fieldCount()) {
            return false;
        }
        return ours.entrySet().stream().allMatch(field ->
                agree.test(field.getValue(), theirs.get(field.getKey())));
    }


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
    public Value make(Value spec, Maker maker) {
        return maker.makeObjectFrom(this, spec);
    }

    @Override
    public Optional<Context> fieldsAsAContext() {
        return Optional.of(context);
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
