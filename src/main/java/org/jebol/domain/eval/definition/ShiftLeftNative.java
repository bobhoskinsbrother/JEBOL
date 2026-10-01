package org.jebol.domain.eval.definition;

public class ShiftLeftNative extends ShiftNative {

    @Override
    public String name() {
        return "shift-left";
    }

    @Override
    protected long movedBy(long bits, long places) {
        return bits << places;
    }
}
