package org.jebol.domain.value;

import org.jebol.domain.eval.GobPath;

import java.util.List;
import java.util.Set;

public record GobValue(GobStorage storage, int index) implements RebolSeries {

    public GobValue {
        if (storage == null) {
            throw new IllegalArgumentException("a gob value needs storage");
        }
    }

    public long positionCountedAsUnsigned() {
        return Integer.toUnsignedLong(index);
    }

    public int positionWithinThePane() {
        long counted = Integer.toUnsignedLong(index - 1) + 1;
        return (int) Math.min(Math.max(counted, 1), storage.length() + 1L);
    }

    public static GobValue empty() {
        return new GobValue(new GobStorage(), 1);
    }

    @Override
    public Datatype datatype() {
        return TYPE;
    }

    public static final Datatype TYPE = new GobDatatype();

    private static final class GobDatatype extends Datatype {

        GobDatatype() {
            super("gob");
        }

        @Override
        public Value madeFrom(Value spec, Maker maker) {
            return switch (spec) {
                case GobValue cloned -> new GobValue(cloned.storage().copyWithoutPane(), 1);
                case PairValue size -> {
                    GobValue made = empty();
                    made.storage().size(size);
                    yield made;
                }
                case BlockValue fields -> {
                    GobValue made = empty();
                    fillFromTheSpec(made, fields.remaining(), maker);
                    yield made;
                }
                default -> throw refusing(spec);
            };
        }

        @Override
        public Value convertedFrom(Value value, Maker maker) {
            throw Raised.of(EvaluationFailure.INVALID_ARG, this);
        }

        private void fillFromTheSpec(GobValue gob, List<Value> spec, Maker maker) {
            for (int at = 0; at < spec.size(); at += 2) {
                Value name = spec.get(at);
                if (!(name instanceof SetWordValue field)) {
                    throw Raised.of(EvaluationFailure.EXPECT_VAL, SetWordValue.TYPE, name.datatype());
                }
                Value given = at + 1 < spec.size() ? spec.get(at + 1) : UnsetValue.unset();
                if (given instanceof UnsetValue || given instanceof SetWordValue) {
                    throw Raised.of(EvaluationFailure.NEED_VALUE, field);
                }
                Value written = maker.simpleValueOf(given);
                if (!GobPath.accepted(gob.storage(), field.canonical(), written)) {
                    throw Raised.of(EvaluationFailure.BAD_FIELD_SET, field, written.datatype());
                }
            }
        }
    }

    @Override
    public RebolSeries skipped(long steps) {
        return atIndex((int) (index + steps));
    }

    @Override
    public Value itemAt(int positionFromTheHead) {
        return storage.childAt(positionFromTheHead);
    }

    @Override
    public Value frontCopied(int howMany, boolean deeply, Set<Datatype> kinds) {
        throw Raised.cannotUseTheAction(this, "copy");
    }

    @Override
    public RebolSeries reversedFront(int howMany) {
        throw Raised.cannotUse(this, "reverse/part");
    }

    @Override
    public RebolSeries reversedFromHere() {
        storage.turnRound();
        return this;
    }

    @Override
    public Value picked(int oneBasedPosition) {
        return childCounted(oneBasedPosition);
    }

    @Override
    public Value pickedBy(Value selector) {
        return childCounted(selector.asPosition());
    }

    public Value childCounted(long count) {
        long at = index - 1 + count;
        return at < 1 || at > storage.length()
                ? NoneValue.none()
                : storage.childAt((int) at);
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public List<Value> items() {
        return storage.pane().subList(
                Math.min(index - 1, storage.length()), storage.length());
    }

    @Override
    public GobValue atIndex(int oneBasedIndex) {
        return new GobValue(storage, oneBasedIndex);
    }

    @Override
    public GobValue head() {
        return atIndex(1);
    }

    @Override
    public GobValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof GobValue gob && gob.storage == storage;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GobValue(GobStorage storage1, int index1)
                && storage1 == storage
                && index1 == index;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(storage) * 31 + index;
    }

    @Override
    public String toString() {
        return "gob " + storage.offset() + " @" + index;
    }
}
