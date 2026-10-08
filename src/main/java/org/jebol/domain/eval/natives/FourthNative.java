package org.jebol.domain.eval.natives;

public class FourthNative extends OrdinalNative {

    @Override
    public String nativeName() {
        return "fourth";
    }

    @Override
    protected int position() {
        return 4;
    }
}
