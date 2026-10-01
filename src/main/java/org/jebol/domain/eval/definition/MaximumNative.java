package org.jebol.domain.eval.definition;

public class MaximumNative extends MinOrMaxNative {

    @Override
    public String name() {
        return "maximum";
    }

    @Override
    protected boolean wantsTheLarger() {
        return true;
    }
}
