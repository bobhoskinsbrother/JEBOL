package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;

public record ModuleValue(Context context, ObjectValue header) implements Value {

    public ModuleValue {
        if (context == null || context.isUnbound()) {
            throw new IllegalArgumentException("a module needs a real context");
        }
        if (header == null) {
            throw new IllegalArgumentException("a module needs a header");
        }
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
    public Value reflected(AnyWordValue field) {
        return switch (field.canonical()) {
            case "spec" -> header;
            case "title" -> headerField("title");
            case "body" -> context.setWordsAndValuesOnLines();
            case "words" -> context.wordsExcludingSelf();
            case "values" -> context.valuesExcludingSelf();
            default -> NoneValue.none();
        };
    }

    public List<String> exportedNames() {
        if (!(headerField("exports") instanceof AnyBlockValue exports)) {
            return List.of();
        }
        return exports.remaining().stream()
                .filter(AnyWordValue.class::isInstance)
                .map(AnyWordValue.class::cast)
                .map(AnyWordValue::canonical)
                .toList();
    }

    public Value headerField(String name) {
        return header.fieldValue(name);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModuleValue(Context context1, ObjectValue header1)
                && context.fieldsExcludingSelf().equals(
                        context1.fieldsExcludingSelf())
                && header.equals(header1);
    }

    @Override
    public int hashCode() {
        return context.fieldsExcludingSelf().hashCode();
    }

    @Override
    public Datatype datatype() {
        return Datatype.MODULE;
    }

    @Override
    public boolean atTail() {
        return context.holdsNothingButSelf();
    }

    @Override
    public String toString() {
        Value declared = headerField("name");
        return declared instanceof AnyWordValue word
                ? "module " + word.canonical()
                : "module with " + context.slotCount() + " words";
    }
}
