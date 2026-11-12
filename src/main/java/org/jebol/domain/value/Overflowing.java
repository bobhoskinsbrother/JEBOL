package org.jebol.domain.value;

import java.util.function.Supplier;

public final class Overflowing {

    private Overflowing() {
    }

    public static Value report(Supplier<Value> counting) {
        try {
            return counting.get();
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW);
        }
    }

    public static void refuseZeroDivisor(double divisor) {
        if (divisor == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    public static void refuseZeroOctetDivisor(double divisor) {
        if (divisor == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    public static double roundHalfAwayFromZero(double amount) {
        return amount < 0 ? -Math.round(-amount) : Math.round(amount);
    }
}
