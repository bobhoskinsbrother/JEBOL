package org.jebol.domain.eval.definition;

public class ArccosineFunction extends InverseTrigonometryFunction {

    @Override
    public String name() {
        return "arccosine";
    }

    @Override
    protected double radiansFor(double ratio) {
        return Math.acos(ratio);
    }
}
