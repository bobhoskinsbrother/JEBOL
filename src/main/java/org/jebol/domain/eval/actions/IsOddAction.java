package org.jebol.domain.eval.actions;

public class IsOddAction extends ParityAction {

    @Override
    public String nativeName() {
        return "odd?";
    }

    @Override
    protected boolean asksForOdd() {
        return true;
    }
}
