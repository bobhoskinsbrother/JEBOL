package org.jebol.domain.value;

import org.jebol.domain.eval.Comparison;

public final class PairActions {

    private final Value left;

    public PairActions(Value left) {
        this.left = left;
    }

    public Value combinedWith(Value right, ArithmeticOperation operation) {
        refuseWhatIsNotAPairOrAPlainNumber(left);
        refuseWhatIsNotAPairOrAPlainNumber(right);
        if (operation.needsANonZeroDivisor()) {
            operation.requireANonZeroDivisor(firstHalfOf(right));
            operation.requireANonZeroDivisor(secondHalfOf(right));
        }
        return PairValue.of(
                halfCombined(firstHalfOf(left), firstHalfOf(right), operation),
                halfCombined(secondHalfOf(left), secondHalfOf(right), operation));
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

    private static double halfCombined(
            double ours, double theirs, ArithmeticOperation operation) {
        return ((AnyDecimalValue) operation.onFractions(ours, theirs))
                .quantity();
    }

    public static double firstHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.x() : Comparison.asDouble(value);
    }

    public static double secondHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.y() : Comparison.asDouble(value);
    }
}
