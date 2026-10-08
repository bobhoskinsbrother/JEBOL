package org.jebol.domain.eval.natives;

public class UnlessNative extends OneBranchNative {

    @Override
    public String nativeName() {
        return "unless";
    }

    @Override
    protected boolean runsTheBranchWhenTheConditionHolds() {
        return false;
    }
}
