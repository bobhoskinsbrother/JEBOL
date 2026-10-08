package org.jebol.domain.eval.natives;

public class SineNative extends TrigonometryNative {

    @Override
    public String nativeName() {
        return "sine";
    }

    @Override
    protected double ratioOf(double radians) {
        double answered = Math.sin(radians);
        return Math.abs(answered) < Math.ulp(1.0) ? 0.0 : answered;
    }
}
