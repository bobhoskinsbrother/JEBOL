package org.jebol.domain.eval.actions;

import org.jebol.domain.value.And;
import org.jebol.domain.value.BitwiseOperation;

public class BitwiseAndAction extends BitwiseAction {

    @Override
    public String nativeName() {
        return "and~";
    }

    @Override
    protected BitwiseOperation operation() {
        return new And();
    }
}
