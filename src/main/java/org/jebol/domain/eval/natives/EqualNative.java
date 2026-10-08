package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;

public class EqualNative extends EqualityNative {

    @Override
    public String nativeName() {
        return "equal?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.EQUAL;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return true;
    }
}
