package org.jebol.domain.eval.definition;

public class ArctangentNative extends InverseTrigonometryNative {

    @Override
    public String name() {
        return "arctangent";
    }

    @Override
    protected double radiansFor(double ratio) {
        return Math.atan(ratio);
    }
}
