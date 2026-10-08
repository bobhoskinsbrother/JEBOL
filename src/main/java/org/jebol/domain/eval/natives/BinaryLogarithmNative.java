package org.jebol.domain.eval.natives;

public class BinaryLogarithmNative extends OneNumberNative {

    @Override
    public String nativeName() {
        return "log-2";
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.log(quantity) / Math.log(2);
    }
}
