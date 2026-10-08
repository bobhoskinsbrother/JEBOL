package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;

public class LesserNative extends OrderNative {

    @Override
    public String nativeName() {
        return "lesser?";
    }

    @Override
    protected Comparison.Strictness strictness() {
        return Comparison.Strictness.GREATER_OR_EQUAL;
    }

    @Override
    protected boolean answersYesWhenItHolds() {
        return false;
    }
}
