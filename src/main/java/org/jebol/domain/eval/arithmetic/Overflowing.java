package org.jebol.domain.eval.arithmetic;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.value.Value;

import java.util.function.Supplier;

final class Overflowing {

    private Overflowing() {
    }

    static Value reported(Supplier<Value> counting) {
        try {
            return counting.get();
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW, overflowed.getMessage());
        }
    }

    static void refuseAZeroDivisor(double divisor) {
        if (divisor == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    static void refuseAZeroOctetDivisor(double divisor) {
        if (divisor == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "tuple");
        }
    }

    static double roundedHalfAwayFromZero(double amount) {
        return amount < 0 ? -Math.round(-amount) : Math.round(amount);
    }
}
