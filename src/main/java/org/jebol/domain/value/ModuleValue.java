package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;

public record ModuleValue(Context context, ObjectValue header) implements Value, PathTargetWithFields {

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
    public Value trim(Trimming trimming) {
        trimming.refuseEveryRefinementOnFields();
        return new ObjectValue(context.withoutTheFieldsHoldingNothing());
    }

    @Override
    public List<Value> listTheItems() {
        return context.listTheBoundWordsAndValues();
    }

    @Override
    public Value reflect(AnyWordValue field) {
        return switch (field.canonical()) {
            case "spec" -> header;
            case "title" -> readHeaderField("title");
            case "body" -> context.setWordsAndValuesOnLines();
            case "words" -> context.wordsExcludingSelf();
            case "values" -> context.valuesExcludingSelf();
            default -> NoneValue.none();
        };
    }

    public List<String> listTheExportedNames() {
        if (!(readHeaderField("exports") instanceof AnyBlockValue exports)) {
            return List.of();
        }
        return exports.remaining().stream()
                .filter(AnyWordValue.class::isInstance)
                .map(AnyWordValue.class::cast)
                .map(AnyWordValue::canonical)
                .toList();
    }

    public Value readHeaderField(String name) {
        return header.readFieldValue(name);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModuleValue(Context context1, ObjectValue header1)
                && context.listTheFieldsExceptSelf().equals(
                        context1.listTheFieldsExceptSelf())
                && header.equals(header1);
    }

    @Override
    public int hashCode() {
        return context.listTheFieldsExceptSelf().hashCode();
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new ModuleDatatype();

    private static final class ModuleDatatype extends AnyObjectDatatype {

        ModuleDatatype() {
            super("module");
        }

        @Override
        public Value makeFrom(Value spec, Maker maker) {
            if (!(spec instanceof AnyBlockValue)) {
                throw refuse(spec);
            }
            if (!(maker.applyASystemFunction("make-module*", spec) instanceof ModuleValue module)) {
                throw Raised.of(EvaluationFailure.INVALID_SPEC, "module!");
            }
            return module;
        }

        @Override
        public Value convert(Value value, Maker maker) {
            if (!(value instanceof BlockValue parts) || parts.remaining().isEmpty()) {
                throw refuse(value);
            }
            List<Value> given = parts.remaining();
            if (!(given.getFirst() instanceof ObjectValue header)) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, given.getFirst());
            }
            if (given.size() < 2 || !(given.get(1) instanceof ObjectValue(Context fields))) {
                throw Raised.of(EvaluationFailure.INVALID_ARG,
                        given.size() < 2 ? given.getFirst() : given.get(1));
            }
            return new ModuleValue(fields, header);
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            if (!(readSpecificationIn(contents) instanceof AnyBlockValue parts)) {
                throw refuseTheConstruction(contents);
            }
            List<Value> given = parts.remaining();
            if (given.size() < 2
                    || !(given.get(0) instanceof ObjectValue header)
                    || !(given.get(1) instanceof ObjectValue(Context fields))) {
                throw refuseTheConstruction(contents);
            }
            return new ModuleValue(fields, header);
        }
    }

    @Override
    public boolean atTail() {
        return context.holdsNothingButSelf();
    }

    @Override
    public String toString() {
        Value declared = readHeaderField("name");
        return declared instanceof AnyWordValue word
                ? "module " + word.canonical()
                : "module with " + context.slotCount() + " words";
    }
}
