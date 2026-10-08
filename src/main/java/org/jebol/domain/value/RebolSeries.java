package org.jebol.domain.value;

import java.util.Set;

public sealed interface RebolSeries extends Value
        permits AnyStringValue, BinaryValue, BlockValue, ImageValue, GobValue, VectorValue {

    int index();

    int storageLength();

    RebolSeries atIndex(int oneBasedIndex);

    boolean sharesStorageWith(RebolSeries other);

    Value itemAt(int positionFromTheHead);

    RebolSeries reversedFront(int howMany);

    Value frontCopied(int howMany, boolean deeply, Set<Datatype> kinds);

    default RebolSeries reversedFromHere() {
        return reversedFront(lengthFromHere());
    }

    default RebolSeries swapFirstItemWith(RebolSeries there) {
        if (!atTail() && !there.atTail()) {
            Value mine = itemAt(index());
            putItemAt(index(), there.itemAt(there.index()));
            there.putItemAt(there.index(), mine);
        }
        return this;
    }

    default void putItemAt(int positionFromTheHead, Value item) {
        throw Raised.cannotUseTheAction(this, "swap");
    }

    default RebolSeries clampedToTail() {
        int tail = storageLength() + 1;
        return index() > tail ? atIndex(tail) : this;
    }

    default RebolSeries reachingBackIfNegative(long wanted) {
        if (wanted >= 0) {
            return this;
        }
        int reaching = (int) Math.min(-wanted, index() - 1L);
        return atIndex(index() - reaching);
    }

    @Override
    default Value picked(int oneBasedPosition) {
        if (oneBasedPosition == 0) {
            return NoneValue.none();
        }
        int counted = oneBasedPosition < 0 ? oneBasedPosition + 1 : oneBasedPosition;
        int at = index() + counted - 1;
        if (at < 1 || at > storageLength()) {
            return NoneValue.none();
        }
        return itemAt(at);
    }

    default int lengthFromHere() {
        return Math.max(0, storageLength() - index() + 1);
    }

    default boolean isPastTheEnd() {
        return index() > storageLength() + 1;
    }

    default boolean atHead() {
        return index() == 1;
    }

    @Override
    default boolean atTail() {
        return index() >= storageLength() + 1;
    }

    default RebolSeries atClamped(long wanted) {
        return atIndex((int) Math.max(1, Math.min(wanted, storageLength() + 1L)));
    }

    default RebolSeries skipped(long steps) {
        return atClamped(index() + steps);
    }

    default long positionNamedBy(Value given, boolean countingFromOne) {
        return switch (given) {
            case PairValue ignored -> throw Raised.of(EvaluationFailure.INVALID_ARG, given);
            case IntegerValue number -> number.magnitude();
            case DecimalValue number -> (long) number.quantity();
            case LogicValue yesOrNo -> (yesOrNo.isTruthy() ? 1 : 2) - (countingFromOne ? 0 : 1);
            default -> 1;
        };
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
            throw Raised.of(EvaluationFailure.INVALID_PART, howMuch);
        }
        return Math.abs(upTo.index() - index());
    }
}
