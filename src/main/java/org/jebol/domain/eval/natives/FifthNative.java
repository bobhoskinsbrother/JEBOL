package org.jebol.domain.eval.natives;

public class FifthNative extends OrdinalNative {

    @Override
    public String nativeName() {
        return "fifth";
    }

    @Override
    protected int position() {
        return 5;
    }
}
