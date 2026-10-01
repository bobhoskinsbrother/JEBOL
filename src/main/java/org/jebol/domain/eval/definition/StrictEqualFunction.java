package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;

public class StrictEqualFunction extends EqualityFunction {

    @Override
    public String name() {
        return "strict-equal?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.STRICT_EQUAL;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return true;
    }
}
