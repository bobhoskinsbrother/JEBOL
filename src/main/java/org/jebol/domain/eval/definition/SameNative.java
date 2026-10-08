package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;

public class SameNative extends EqualityNative {

    @Override
    public String nativeName() {
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
