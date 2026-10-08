package org.jebol.domain.eval.natives;

public class TenthNative extends OrdinalNative {

    @Override
    public String nativeName() {
        return "tenth";
    }

    @Override
    protected int position() {
        return 10;
    }
}
