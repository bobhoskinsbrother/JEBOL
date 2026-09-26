package org.jebol.domain.eval;

import org.jebol.domain.eval.arithmetic.ArithmeticOperation;

import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Value;

public final class PairActions {

    private final Value left;

    public PairActions(Value left) {
        this.left = left;
    }

    Value combinedWith(Value right, ArithmeticOperation operation) {
        refuseWhatIsNotAPairOrAPlainNumber(left);
        refuseWhatIsNotAPairOrAPlainNumber(right);
        if (operation.needsANonZeroDivisor()) {
            Arithmetic.requireNonZero(firstHalfOf(right));
            Arithmetic.requireNonZero(secondHalfOf(right));
        }
        return PairValue.of(
                halfCombined(firstHalfOf(left), firstHalfOf(right), operation),
                halfCombined(secondHalfOf(left), secondHalfOf(right), operation));
    }

    private static void refuseWhatIsNotAPairOrAPlainNumber(Value side) {
        if (side instanceof PairValue
                || side instanceof IntegerValue
                || side instanceof DecimalValue) {
            return;
        }
        throw Raised.of(EvaluationFailure.NOT_RELATED,
                side.datatype().literalSpelling() + " does not go with pair arithmetic");
    }

    private static double halfCombined(
            double ours, double theirs, ArithmeticOperation operation) {
        return ((DecimalValue) Arithmetic.decimalCombined(ours, theirs, operation))
                .quantity();
    }

    static double firstHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.x() : Comparison.asDouble(value);
    }

    static double secondHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.y() : Comparison.asDouble(value);
    }
}
