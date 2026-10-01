package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;

public class NotEquivNative extends EqualityNative {

    @Override
    public String name() {
        return "not-equiv?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.EQUIV;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return false;
    }
}
