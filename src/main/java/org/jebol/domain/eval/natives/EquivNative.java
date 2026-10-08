package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;

public class EquivNative extends EqualityNative {

    @Override
    public String nativeName() {
        return "equiv?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.EQUIV;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return true;
    }
}
