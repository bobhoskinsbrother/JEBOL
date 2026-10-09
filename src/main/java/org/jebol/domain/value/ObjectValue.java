package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public record ObjectValue(Context context) implements Value, PathTargetWithFields {

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
        return switch (spec) {
            case BlockValue body -> maker.makeObjectFrom(this, body);
            case NoneValue nothing -> maker.makeObjectFrom(this, BlockValue.block(List.of()));
            case ObjectValue other -> maker.objectMergedFrom(this, other);
            default -> throw Raised.of(EvaluationFailure.BAD_MAKE_ARG, TYPE, this);
        };
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
        fields.pointItsOwnSelfAt(duplicate);
        context.slots()
                .forEach(slot -> fields.register(slot.spelling(),
                        slot.value().copiedAsAMember(deeply, kinds)));
        return duplicate;
    }

    @Override
    public Value reflected(AnyWordValue field) {
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
        List<Value> written = target instanceof AnyBlockValue pairs
                ? pairs.remaining()
                : List.of(target);
        for (Value each : written) {
            if (each instanceof AnyWordValue word && hidesTheDeclaredField(word)) {
                throw Raised.of(EvaluationFailure.HIDDEN, word.spelling());
            }
        }
    }

    private Stream<ContextSlot> fieldsOtherThanSelf() {
        return context.slots().stream();
    }

    private boolean hidesTheDeclaredField(AnyWordValue word) {
        return context.everySlot().stream().anyMatch(
                slot -> slot.isHidden() && slot.canonical().equals(word.canonical()));
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new ObjectDatatype();

    private static final class ObjectDatatype extends AnyObjectDatatype {

        ObjectDatatype() {
            super("object");
        }

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            return switch (spec) {
                case AnyBlockValue body -> maker.objectEvaluatedFrom(body);
                case IntegerValue(long magnitude) -> anEmptyObjectWithRoomFor(spec, magnitude, maker);
                case DecimalValue number -> anEmptyObjectWithRoomFor(spec, number.quantity(), maker);
                case MapValue map -> anObjectOfTheWordKeysIn(map, maker);
                default -> throw refusing(spec);
            };
        }

        private Value anObjectOfTheWordKeysIn(MapValue map, Maker maker) {
            ObjectValue made = maker.objectEvaluatedFrom(BlockValue.block());
            List<Value> keys = map.keys();
            List<Value> values = map.values();
            for (int at = 0; at < keys.size(); at++) {
                if (keys.get(at) instanceof AnyWordValue word && !(values.get(at) instanceof NoneValue)) {
                    made.context().register(word.spelling(), values.get(at));
                }
            }
            return made;
        }

        private Value anEmptyObjectWithRoomFor(Value spec, double asked, Maker maker) {
            if (asked < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, spec);
            }
            return maker.objectEvaluatedFrom(BlockValue.block());
        }

        @Override
        protected Value built(Conversion asking, Value from, Maker maker) {
            if (!(from instanceof ErrorValue raised)) {
                throw refusing(from);
            }
            if (raised.field("code").orElseGet(NoneValue::none) instanceof IntegerValue(long magnitude)
                    && magnitude < ErrorCatalogue.LOWEST_CODE_AN_ENTRY_HAS) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, from);
            }
            Context fields = Context.childOf(Context.root());
            for (String field : ErrorValue.FIELDS) {
                fields.register(field, raised.field(field).orElseGet(NoneValue::none));
            }
            return new ObjectValue(fields);
        }

        @Override
        public Value constructedFrom(List<Value> contents, Construction construction) {
            if (contents.size() != 1 || !(contents.getFirst() instanceof AnyBlockValue fields)) {
                throw refusingConstruction(contents);
            }
            Context built = Context.root();
            List<Value> items = fields.remaining();
            for (int at = 0; at < items.size(); at++) {
                if (!(items.get(at) instanceof SetWordValue name)) {
                    throw refusingConstruction(contents);
                }
                at++;
                built.register(name.spelling(), at < items.size() ? items.get(at) : NoneValue.none());
            }
            return new ObjectValue(built);
        }
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
