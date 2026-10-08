package org.jebol.domain.eval.actions;

public class IsEvenAction extends ParityAction {

    @Override
    public String nativeName() {
        return "even?";
    }

    @Override
    protected boolean asksForOdd() {
        return false;
    }
}
