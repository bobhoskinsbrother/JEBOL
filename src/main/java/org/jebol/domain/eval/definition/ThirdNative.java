package org.jebol.domain.eval.definition;

public class ThirdNative extends OrdinalNative {

    @Override
    public String nativeName() {
        return "third";
    }

    @Override
    protected int position() {
        return 3;
    }
}
