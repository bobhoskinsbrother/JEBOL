package org.jebol.domain.eval.definition;

public class ThirdNative extends OrdinalNative {

    @Override
    public String name() {
        return "third";
    }

    @Override
    protected int position() {
        return 3;
    }
}
