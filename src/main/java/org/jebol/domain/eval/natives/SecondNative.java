package org.jebol.domain.eval.natives;

public class SecondNative extends OrdinalNative {

    @Override
    public String nativeName() {
        return "second";
    }

    @Override
    protected int position() {
        return 2;
    }
}
