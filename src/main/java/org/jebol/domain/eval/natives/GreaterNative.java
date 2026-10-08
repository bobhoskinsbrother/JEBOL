package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;

public class GreaterNative extends OrderNative {

    @Override
    public String nativeName() {
        return "greater?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.GREATER;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return true;
    }
}
