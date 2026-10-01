package org.jebol.domain.eval.definition;

public class ArccosineNative extends InverseTrigonometryNative {

    @Override
    public String name() {
        return "arccosine";
    }

    @Override
    protected double radiansFor(double ratio) {
        return Math.acos(ratio);
    }
}
