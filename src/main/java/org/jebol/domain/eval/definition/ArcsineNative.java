package org.jebol.domain.eval.definition;

public class ArcsineNative extends InverseTrigonometryNative {

    @Override
    public String name() {
        return "arcsine";
    }

    @Override
    protected double radiansFor(double ratio) {
        return Math.asin(ratio);
    }
}
