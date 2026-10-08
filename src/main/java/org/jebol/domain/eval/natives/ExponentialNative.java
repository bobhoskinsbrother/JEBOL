package org.jebol.domain.eval.natives;

public class ExponentialNative extends OneNumberNative {

    @Override
    public String nativeName() {
        return "exp";
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.exp(quantity);
    }
}
