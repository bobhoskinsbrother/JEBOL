package org.jebol.domain.eval.natives;

public class CommonLogarithmNative extends OneNumberNative {

    @Override
    public String nativeName() {
        return "log-10";
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.log10(quantity);
    }
}
