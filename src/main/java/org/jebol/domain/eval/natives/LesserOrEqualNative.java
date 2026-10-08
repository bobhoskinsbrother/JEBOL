package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;

public class LesserOrEqualNative extends OrderNative {

    @Override
    public String nativeName() {
        return "lesser-or-equal?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.GREATER;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return false;
    }
}
