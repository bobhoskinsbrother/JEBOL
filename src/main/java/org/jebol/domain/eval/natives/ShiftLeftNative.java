package org.jebol.domain.eval.natives;

public class ShiftLeftNative extends ShiftingNative {

    @Override
    public String nativeName() {
        return "shift-left";
    }

    @Override
    protected long moveBy(long bits, long places) {
        return bits << places;
    }
}
