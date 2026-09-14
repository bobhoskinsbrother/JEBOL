package org.jebol.domain.eval;

import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Value;

/**
 * What a point does when an action is performed on it, which is what
 * {@code REBTYPE(Pair)} answers in {@code t-pair.c}.
 *
 * <p>A pair works half by half: both halves of the answer come from the same
 * operation applied separately, and a plain number on either side stands in
 * for a pair with that number in both halves. Nothing else goes with a pair
 * at all -- not a time, not a tuple, not a money -- and both halves of a
 * divisor have to be non-zero before either half is worked out, so a pair
 * with one zero half fails rather than answering half an answer.
 */
public final class PairActions {

    private final Value left;

    public PairActions(Value left) {
        this.left = left;
    }

    /** ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER and MODULO, half by half. */
    Value combinedWith(Value right, Arithmetic.Operation operation) {
        refuseWhatIsNotAPairOrAPlainNumber(left);
        refuseWhatIsNotAPairOrAPlainNumber(right);
        if (operation == Arithmetic.Operation.DIVIDE
                || operation == Arithmetic.Operation.REMAINDER
                || operation == Arithmetic.Operation.MODULO) {
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
            double ours, double theirs, Arithmetic.Operation operation) {
        return ((DecimalValue) Arithmetic.decimalCombined(ours, theirs, operation))
                .quantity();
    }

    /** The x half, or a plain number standing in for both halves. */
    static double firstHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.x() : Comparison.asDouble(value);
    }

    /** The y half, or a plain number standing in for both halves. */
    static double secondHalfOf(Value value) {
        return value instanceof PairValue pair ? pair.y() : Comparison.asDouble(value);
    }
}
