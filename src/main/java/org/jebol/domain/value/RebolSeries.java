package org.jebol.domain.value;

public sealed interface RebolSeries extends Value
        permits StringValue, BinaryValue, BlockValue, ImageValue, GobValue, VectorValue {

    int index();

    int storageLength();

    RebolSeries atIndex(int oneBasedIndex);

    boolean sharesStorageWith(RebolSeries other);

    default int lengthFromHere() {
        return Math.max(0, storageLength() - index() + 1);
    }

    default boolean isPastTheEnd() {
        return index() > storageLength() + 1;
    }

    default boolean atHead() {
        return index() == 1;
    }

    default boolean atTail() {
        return index() >= storageLength() + 1;
    }

    default RebolSeries head() {
        return atIndex(1);
    }

    default RebolSeries tail() {
        return atIndex(storageLength() + 1);
    }

    default void refuseANeedleItCannotHold(Value needle, String nativeName) {
    }

    default RebolSeries earlierOf(RebolSeries other) {
        return other.index() < index() ? other : this;
    }

    default long countUpTo(Value howMuch) {
        if (howMuch instanceof IntegerValue count) {
            if (count.magnitude() < Integer.MIN_VALUE
                    || count.magnitude() > Integer.MAX_VALUE) {
                throw Raised.of(EvaluationFailure.OUT_OF_RANGE, Molder.mold(count));
            }
            return count.magnitude();
        }
        if (howMuch instanceof DecimalValue(double quantity, Datatype datatype)
                && datatype != Datatype.PERCENT) {
            return (long) quantity;
        }
        if (!(howMuch instanceof RebolSeries upTo) || !sharesStorageWith(upTo)) {
            throw Raised.of(EvaluationFailure.INVALID_PART, Molder.mold(howMuch));
        }
        return Math.abs(upTo.index() - index());
    }
}
