package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;

public class GreaterFunction extends OrderFunction {

    @Override
    public String name() {
        return "greater?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.GREATER;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return true;
    }
}
