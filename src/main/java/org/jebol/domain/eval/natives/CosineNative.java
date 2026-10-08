package org.jebol.domain.eval.natives;

public class CosineNative extends TrigonometryNative {

    @Override
    public String nativeName() {
        return "cosine";
    }

    @Override
    protected double ratioOf(double radians) {
        double answered = Math.cos(radians);
        return Math.abs(answered) < Math.ulp(1.0) ? 0.0 : answered;
    }
}
