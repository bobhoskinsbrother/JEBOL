package org.jebol.domain.eval.compression.brotli;

import java.math.BigDecimal;

final class ExactFusedMultiplyAdd {

    private ExactFusedMultiplyAdd() {
    }

    static double of(double first, double second, double third) {
        if (!Double.isFinite(first) || !Double.isFinite(second) || !Double.isFinite(third)) {
            return first * second + third;
        }
        return new BigDecimal(first)
                .multiply(new BigDecimal(second))
                .add(new BigDecimal(third))
                .doubleValue();
    }
}
