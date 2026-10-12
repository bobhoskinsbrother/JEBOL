package org.jebol.domain.eval.compression.brotli;

final class FusedMultiplyAdd {

    private FusedMultiplyAdd() {
    }

    static double of(double first, double second, double third) {
        return Math.fma(first, second, third);
    }
}
