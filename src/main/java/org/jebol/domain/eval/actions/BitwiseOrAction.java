package org.jebol.domain.eval.actions;

import org.jebol.domain.value.BitwiseOperation;
import org.jebol.domain.value.Or;

public class BitwiseOrAction extends BitwiseAction {

    @Override
    public String nativeName() {
        return "or~";
    }

    @Override
    protected BitwiseOperation operation() {
        return new Or();
    }
}
