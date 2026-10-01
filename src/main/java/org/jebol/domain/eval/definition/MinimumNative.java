package org.jebol.domain.eval.definition;

public class MinimumNative extends MinOrMaxNative {

    @Override
    public String name() {
        return "minimum";
    }

    @Override
    protected boolean wantsTheLarger() {
        return false;
    }
}
