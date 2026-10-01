package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;

public class GreaterOrEqualNative extends OrderNative {

    @Override
    public String name() {
        return "greater-or-equal?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.GREATER_OR_EQUAL;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return true;
    }
}
