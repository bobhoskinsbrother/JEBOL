package org.jebol.domain.eval.definition;

public class MaximumFunction extends MinOrMaxFunction {

    @Override
    public String name() {
        return "maximum";
    }

    @Override
    protected boolean wantsTheLarger() {
        return true;
    }
}
