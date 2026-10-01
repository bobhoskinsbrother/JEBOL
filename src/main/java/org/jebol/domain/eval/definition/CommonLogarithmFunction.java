package org.jebol.domain.eval.definition;

public class CommonLogarithmFunction extends OneNumberFunction {

    @Override
    public String name() {
        return "log-10";
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.log10(quantity);
    }
}
