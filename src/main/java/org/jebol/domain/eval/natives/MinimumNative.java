package org.jebol.domain.eval.natives;

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
