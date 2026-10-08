package org.jebol.domain.eval.definition;

public class ShiftRightNative extends ShiftingNative {

    @Override
    public String nativeName() {
        return "shift-right";
    }

    @Override
    protected long movedBy(long bits, long places) {
        return bits >> places;
    }
}
