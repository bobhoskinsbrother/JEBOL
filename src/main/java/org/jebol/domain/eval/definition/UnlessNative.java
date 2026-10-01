package org.jebol.domain.eval.definition;

public class UnlessNative extends OneBranchNative {

    @Override
    public String name() {
        return "unless";
    }

    @Override
    protected boolean runsTheBranchWhenTheConditionHolds() {
        return false;
    }
}
