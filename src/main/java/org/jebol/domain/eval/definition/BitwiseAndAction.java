package org.jebol.domain.eval.definition;

import org.jebol.domain.value.And;
import org.jebol.domain.value.BitwiseOperation;

public class BitwiseAndAction extends BitwiseAction {

    @Override
    public String name() {
        return "and~";
    }

    @Override
    protected BitwiseOperation operation() {
        return new And();
    }
}
