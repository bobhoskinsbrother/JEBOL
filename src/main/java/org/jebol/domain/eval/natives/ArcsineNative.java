package org.jebol.domain.eval.natives;

public class ArcsineNative extends InverseTrigonometryNative {

    @Override
    public String nativeName() {
        return "arcsine";
    }

    @Override
    protected double measureRadiansFor(double ratio) {
        return Math.asin(ratio);
    }
}
