package org.jebol.domain.eval.definition;

public class IsEvenAction extends ParityAction {

    @Override
    public String name() {
        return "even?";
    }

    @Override
    protected boolean asksForOdd() {
        return false;
    }
}
