package org.jebol.domain.value;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public record ObjectValue(Context context) implements Value, PathTargetWithFields {

    @Override
    public boolean isEqualTo(Value other, Sameness how) {
        return other instanceof ObjectValue theirs
                && agreesInEveryField(theirs, (ours, yours) ->
                        ours.isEqualTo(yours, Sameness.insideSeries()));
    }

    public boolean agreesInEveryField(
            ObjectValue other, java.util.function.BiPredicate<Value, Value> agree) {

        if (other.context == context) {
            return true;
        }
        java.util.Map<String, Value> ours = context.listFieldsExceptSelf();
        java.util.Map<String, Value> theirs = other.context.listFieldsExceptSelf();
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
        return other instanceof ObjectValue theirs
                && agreesInEveryField(theirs, Object::equals);
    }

    @Override
    public int hashCode() {
        return context.listFieldsExceptSelf().keySet().hashCode();
    }

    @Override
    public Value make(Value spec, Maker maker) {
        return switch (spec) {
            case BlockValue body -> maker.makeObjectFrom(this, body);
            case NoneValue nothing -> maker.makeObjectFrom(this, BlockValue.of(List.of()));
            case ObjectValue other -> maker.mergeObject(this, other);
            default -> throw Raised.of(EvaluationFailure.BAD_MAKE_ARG, TYPE, this);
        };
    }

    @Override
    public Optional<Context> fieldsAsContext() {
        return Optional.of(context);
    }

    @Override
    public Value trim(Trimming trimming) {
        trimming.refuseEveryRefinementOnFields();
        return new ObjectValue(context.withoutFieldsHoldingNothing());
    }

    @Override
    public List<Value> listItems() {
        return context.listBoundWordsAndValues();
    }

    @Override
    public Value copy(boolean deeply, Set<Datatype> kinds) {
        Context fields = Context.root();
        ObjectValue duplicate = new ObjectValue(fields);
        fields.pointItsOwnSelfAt(duplicate);
        context.slots()
                .forEach(slot -> fields.register(slot.spelling(),
                        slot.value().copyAsMember(deeply, kinds)));
        return duplicate;
    }

    @Override
    public Value reflect(AnyWordValue field) {
        return switch (field.canonical()) {
            case "body" -> context.setWordsAndValuesOnLines();
            case "words" -> BlockValue.of(fieldsOtherThanSelf()
                    .<Value>map(slot -> WordValue.of(slot.spelling()).bindTo(context))
                    .toList());
            case "values" -> BlockValue.of(fieldsOtherThanSelf()
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
            if (each instanceof AnyWordValue word && hidesDeclaredField(word)) {
                throw Raised.of(EvaluationFailure.HIDDEN, word.spelling());
            }
        }
    }

    private Stream<ContextSlot> fieldsOtherThanSelf() {
        return context.slots().stream();
    }

    private boolean hidesDeclaredField(AnyWordValue word) {
        return context.listEverySlot().stream().anyMatch(
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
        public Value makeFrom(Value spec, Maker maker) {
            return switch (spec) {
                case AnyBlockValue body -> maker.evaluateObject(body);
                case IntegerValue(long magnitude) -> makeEmptyObjectWithRoomFor(spec, magnitude, maker);
                case DecimalValue number -> makeEmptyObjectWithRoomFor(spec, number.quantity(), maker);
                case MapValue map -> objectFromWordKeys(map, maker);
                default -> throw refuse(spec);
            };
        }

        private Value objectFromWordKeys(MapValue map, Maker maker) {
            ObjectValue made = maker.evaluateObject(BlockValue.of());
            List<Value> keys = map.keys();
            List<Value> values = map.values();
            for (int at = 0; at < keys.size(); at++) {
                if (keys.get(at) instanceof AnyWordValue word && !(values.get(at) instanceof NoneValue)) {
                    made.context().register(word.spelling(), values.get(at));
                }
            }
            return made;
        }

        private Value makeEmptyObjectWithRoomFor(Value spec, double asked, Maker maker) {
            if (asked < 0) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, spec);
            }
            return maker.evaluateObject(BlockValue.of());
        }

        @Override
        protected Value build(Conversion asking, Value from, Maker maker) {
            if (!(from instanceof ErrorValue raised)) {
                throw refuse(from);
            }
            if (raised.readField("code").orElseGet(NoneValue::none) instanceof IntegerValue(long magnitude)
                    && magnitude < ErrorCatalogue.LOWEST_CODE_ENTRY_HAS) {
                throw Raised.of(EvaluationFailure.INVALID_ARG, from);
            }
            Context fields = Context.makeChildOf(Context.root());
            for (String field : ErrorValue.FIELDS) {
                fields.register(field, raised.readField(field).orElseGet(NoneValue::none));
            }
            return new ObjectValue(fields);
        }

        @Override
        public Value construct(List<Value> contents, Construction construction) {
            if (contents.size() != 1 || !(contents.getFirst() instanceof AnyBlockValue fields)) {
                throw refuseConstruction(contents);
            }
            Context built = Context.root();
            List<Value> items = fields.remaining();
            for (int at = 0; at < items.size(); at++) {
                if (!(items.get(at) instanceof SetWordValue name)) {
                    throw refuseConstruction(contents);
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
