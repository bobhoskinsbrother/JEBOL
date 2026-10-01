package org.jebol.domain.eval.definition;

public class ShiftRightFunction extends ShiftFunction {

    @Override
    public String name() {
        return "shift-right";
    }

    @Override
    protected long movedBy(long bits, long places) {
        return bits >> places;
    }
}
