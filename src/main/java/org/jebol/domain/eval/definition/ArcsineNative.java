package org.jebol.domain.eval.definition;

public class ArcsineNative extends InverseTrigonometryNative {

    @Override
    public String nativeName() {
        return "arcsine";
    }

    @Override
    protected double radiansFor(double ratio) {
        return Math.asin(ratio);
    }
}
