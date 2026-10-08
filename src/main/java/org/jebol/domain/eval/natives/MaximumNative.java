package org.jebol.domain.eval.natives;

public class MaximumNative extends MinOrMaxNative {

    @Override
    public String nativeName() {
        return "maximum";
    }

    @Override
    protected boolean wantsTheLarger() {
        return true;
    }
}
