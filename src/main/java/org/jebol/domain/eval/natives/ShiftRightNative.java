package org.jebol.domain.eval.natives;

public class ShiftRightNative extends ShiftingNative {

    @Override
    public String nativeName() {
        return "shift-right";
    }

    @Override
    protected long moveBy(long bits, long places) {
        return bits >> places;
    }
}
