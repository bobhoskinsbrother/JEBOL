package org.jebol.domain.eval.natives;

public class NaturalLogarithmNative extends OneNumberNative {

    @Override
    public String nativeName() {
        return "log-e";
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.log(quantity);
    }
}
