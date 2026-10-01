package org.jebol.domain.eval.definition;

public class ArcsineFunction extends InverseTrigonometryFunction {

    @Override
    public String name() {
        return "arcsine";
    }

    @Override
    protected double radiansFor(double ratio) {
        return Math.asin(ratio);
    }
}
