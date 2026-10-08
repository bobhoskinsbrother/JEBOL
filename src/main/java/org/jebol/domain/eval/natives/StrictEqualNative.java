package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;

public class StrictEqualNative extends EqualityNative {

    @Override
    public String nativeName() {
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
