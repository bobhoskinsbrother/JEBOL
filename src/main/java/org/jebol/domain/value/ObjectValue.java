package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

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
    public Value trimmed(Trimming trimming) {
        trimming.refuseEveryRefinementOnFields();
        return new ObjectValue(context.withoutTheFieldsHoldingNothing());
    }

    @Override
    public List<Value> items() {
        return context.boundWordsAndValues();
    }

    @Override
    public Value copied(boolean deeply, Set<Datatype> kinds) {
        Context fields = Context.root();
        ObjectValue duplicate = new ObjectValue(fields);
        fields.register("self", duplicate);
        context.slots().stream()
                .filter(slot -> !slot.canonical().equals("self"))
                .forEach(slot -> fields.register(slot.spelling(),
                        slot.value().copiedAsAMember(deeply, kinds)));
        return duplicate;
    }

    @Override
    public Value reflected(WordValue field) {
        return switch (field.canonical()) {
            case "body" -> context.setWordsAndValuesOnLines();
            case "words" -> BlockValue.block(fieldsOtherThanSelf()
                    .<Value>map(slot -> WordValue.of(slot.spelling()).boundTo(context))
                    .toList());
            case "values" -> BlockValue.block(fieldsOtherThanSelf()
                    .map(ContextSlot::value)
                    .toList());
            default -> NoneValue.none();
        };
    }

    public void refuseHiddenFieldsIn(Value target) {
        List<Value> written = target instanceof BlockValue pairs
                ? pairs.remaining()
                : List.of(target);
        for (Value each : written) {
            if (each instanceof WordValue word && hidesTheDeclaredField(word)) {
                throw Raised.of(EvaluationFailure.HIDDEN, word.spelling());
            }
        }
    }

    private Stream<ContextSlot> fieldsOtherThanSelf() {
        return context.slots().stream().filter(slot -> !slot.canonical().equals("self"));
    }

    private boolean hidesTheDeclaredField(WordValue word) {
        return context.everySlot().stream().anyMatch(
                slot -> slot.isHidden() && slot.canonical().equals(word.canonical()));
    }

    @Override
    public Datatype datatype() {
        return Datatype.OBJECT;
    }

    @Override
    public boolean atTail() {
        return context.holdsNothingButSelf();
    }

    @Override
    public String toString() {
        return "object with " + context.slotCount() + " fields";
    }
}
