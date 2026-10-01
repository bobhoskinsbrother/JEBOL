package org.jebol.domain.eval.definition;

public class CommonLogarithmNative extends OneNumberNative {

    @Override
    public String name() {
        return "log-10";
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.log10(quantity);
    }
}
