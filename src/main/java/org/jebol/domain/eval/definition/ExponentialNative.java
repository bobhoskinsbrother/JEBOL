package org.jebol.domain.eval.definition;

public class ExponentialNative extends OneNumberNative {

    @Override
    public String name() {
        return "exp";
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.exp(quantity);
    }
}
