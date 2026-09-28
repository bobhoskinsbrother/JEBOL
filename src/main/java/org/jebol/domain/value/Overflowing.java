package org.jebol.domain.value;

import java.util.function.Supplier;

public final class Overflowing {

    private Overflowing() {
    }

    public static Value reported(Supplier<Value> counting) {
        try {
            return counting.get();
        } catch (ArithmeticException overflowed) {
            throw Raised.of(EvaluationFailure.OVERFLOW, overflowed.getMessage());
        }
    }

    public static void refuseAZeroDivisor(double divisor) {
        if (divisor == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE);
        }
    }

    public static void refuseAZeroOctetDivisor(double divisor) {
        if (divisor == 0) {
            throw Raised.of(EvaluationFailure.ZERO_DIVIDE, "tuple");
        }
    }

    public static double roundedHalfAwayFromZero(double amount) {
        return amount < 0 ? -Math.round(-amount) : Math.round(amount);
    }
}
