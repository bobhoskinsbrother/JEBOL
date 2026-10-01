package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;

public class SameFunction extends EqualityFunction {

    @Override
    public String name() {
        return "same?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.SAME;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return true;
    }
}
