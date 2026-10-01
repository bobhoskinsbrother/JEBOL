package org.jebol.domain.eval.definition;

public class ArctangentFunction extends InverseTrigonometryFunction {

    @Override
    public String name() {
        return "arctangent";
    }

    @Override
    protected double radiansFor(double ratio) {
        return Math.atan(ratio);
    }
}
