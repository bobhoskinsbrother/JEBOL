package org.jebol.domain.eval.definition;

public class MinimumNative extends MinOrMaxNative {

    @Override
    public String nativeName() {
        return "minimum";
    }

    @Override
    protected boolean wantsTheLarger() {
        return false;
    }
}
