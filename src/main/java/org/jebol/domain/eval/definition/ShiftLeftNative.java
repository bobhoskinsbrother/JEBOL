package org.jebol.domain.eval.definition;

public class ShiftLeftNative extends ShiftingNative {

    @Override
    public String nativeName() {
        return "shift-left";
    }

    @Override
    protected long movedBy(long bits, long places) {
        return bits << places;
    }
}
