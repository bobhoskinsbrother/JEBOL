package org.jebol.domain.eval.definition;

public class SecondNative extends OrdinalNative {

    @Override
    public String name() {
        return "second";
    }

    @Override
    protected int position() {
        return 2;
    }
}
