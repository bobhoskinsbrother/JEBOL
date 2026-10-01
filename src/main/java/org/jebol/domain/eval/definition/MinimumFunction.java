package org.jebol.domain.eval.definition;

public class MinimumFunction extends MinOrMaxFunction {

    @Override
    public String name() {
        return "minimum";
    }

    @Override
    protected boolean wantsTheLarger() {
        return false;
    }
}
