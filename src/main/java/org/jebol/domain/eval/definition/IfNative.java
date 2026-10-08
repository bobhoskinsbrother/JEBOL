package org.jebol.domain.eval.definition;

public class IfNative extends OneBranchNative {

    @Override
    public String nativeName() {
        return "if";
    }

    @Override
    protected boolean runsTheBranchWhenTheConditionHolds() {
        return true;
    }
}
