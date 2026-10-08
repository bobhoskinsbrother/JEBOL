package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;

public class NotEquivNative extends EqualityNative {

    @Override
    public String nativeName() {
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
