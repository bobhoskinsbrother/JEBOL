package org.jebol.domain.eval.natives;

public class ArctangentNative extends InverseTrigonometryNative {

    @Override
    public String nativeName() {
        return "arctangent";
    }

    @Override
    protected double radiansFor(double ratio) {
        return Math.atan(ratio);
    }
}
