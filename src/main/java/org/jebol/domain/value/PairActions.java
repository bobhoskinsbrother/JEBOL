package org.jebol.domain.value;

import org.jebol.domain.eval.Comparison;

public final class PairActions {

    private final Value left;

    public PairActions(Value left) {
        this.left = left;
    }

    public Value combineWith(Value right, ArithmeticOperation operation) {
        refuseWhatIsNotAPairOrAPlainNumber(left);
        refuseWhatIsNotAPairOrAPlainNumber(right);
        if (operation.needsNonZeroDivisor()) {
            operation.requireNonZeroDivisor(readTheFirstHalfOf(right));
            operation.requireNonZeroDivisor(readTheSecondHalfOf(right));
        }
        return PairValue.of(
                combineAHalf(readTheFirstHalfOf(left), readTheFirstHalfOf(right), operation),
                combineAHalf(readTheSecondHalfOf(left), readTheSecondHalfOf(right), operation));
    }

    private static void refuseWhatIsNotAPairOrAPlainNumber(Value side) {
        if (side instanceof PairValue
                || side instanceof IntegerValue
                || side instanceof AnyDecimalValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.NOT_RELATED,
                side.datatype().literalSpelling() + " does not go with pair arithmetic");
    }

    private static double combineAHalf(
            double ours, double theirs, ArithmeticOperation operation) {
        return ((AnyDecimalValue) operation.onFractions(ours, theirs))
                .quantity();
    }

    public static double readTheFirstHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.x() : Comparison.asDouble(value);
    }

    public static double readTheSecondHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.y() : Comparison.asDouble(value);
    }
}
