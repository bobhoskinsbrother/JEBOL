package org.jebol.domain.eval.natives;

public class FirstNative extends OrdinalNative {

    @Override
    public String nativeName() {
        return "first";
    }

    @Override
    protected int position() {
        return 1;
    }
}
