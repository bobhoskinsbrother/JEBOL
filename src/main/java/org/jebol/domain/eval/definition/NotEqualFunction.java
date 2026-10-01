package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;

public class NotEqualFunction extends EqualityFunction {

    @Override
    public String name() {
        return "not-equal?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.EQUAL;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return false;
    }
}
