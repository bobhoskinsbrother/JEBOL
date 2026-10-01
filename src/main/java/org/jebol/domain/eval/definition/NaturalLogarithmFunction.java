package org.jebol.domain.eval.definition;

public class NaturalLogarithmFunction extends OneNumberFunction {

    @Override
    public String name() {
        return "log-e";
    }

    @Override
    protected double answerFor(double quantity) {
        return Math.log(quantity);
    }
}
