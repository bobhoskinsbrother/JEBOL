package org.jebol.domain.eval.definition;

public class ShiftLeftFunction extends ShiftFunction {

    @Override
    public String name() {
        return "shift-left";
    }

    @Override
    protected long movedBy(long bits, long places) {
        return bits << places;
    }
}
