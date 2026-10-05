package org.jebol.domain.eval.definition;

public class DecrementNative extends SteppingNative {

    @Override
    public String name() {
        return "--";
    }

    @Override
    protected int step() {
        return -1;
    }
}
