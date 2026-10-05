package org.jebol.domain.eval.definition;

public class WhetherOddAction extends ParityAction {

    @Override
    public String name() {
        return "odd?";
    }

    @Override
    protected boolean asksForOdd() {
        return true;
    }
}
