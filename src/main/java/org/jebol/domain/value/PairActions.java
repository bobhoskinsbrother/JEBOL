package org.jebol.domain.value;

import org.jebol.domain.eval.Comparison;

public final class PairActions {

    private final Value left;

    public PairActions(Value left) {
        this.left = left;
    }

    public Value combineWith(Value right, ArithmeticOperation operation) {
        requirePairOrNumber(left);
        requirePairOrNumber(right);
        if (operation.needsNonZeroDivisor()) {
            operation.requireNonZeroDivisor(firstHalf(right));
            operation.requireNonZeroDivisor(secondHalf(right));
        }
        return PairValue.of(
                combineHalf(firstHalf(left), firstHalf(right), operation),
                combineHalf(secondHalf(left), secondHalf(right), operation));
    }

    private static void requirePairOrNumber(Value side) {
        if (side instanceof PairValue
                || side instanceof IntegerValue
                || side instanceof AnyDecimalValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.NOT_RELATED,
                side.datatype().literalSpelling() + " does not go with pair arithmetic");
    }

    private static double combineHalf(
            double ours, double theirs, ArithmeticOperation operation) {
        return ((AnyDecimalValue) operation.onFractions(ours, theirs))
                .quantity();
    }

    public static double firstHalf(Value value) {
        return value instanceof PairValue pair ? pair.x() : Comparison.asDouble(value);
    }

    public static double secondHalf(Value value) {
        return value instanceof PairValue pair ? pair.y() : Comparison.asDouble(value);
    }
}
