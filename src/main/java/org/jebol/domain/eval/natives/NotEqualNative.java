package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;

public class NotEqualNative extends EqualityNative {

    @Override
    public String nativeName() {
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
