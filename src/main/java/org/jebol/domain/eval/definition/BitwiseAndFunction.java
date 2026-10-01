package org.jebol.domain.eval.definition;

import org.jebol.domain.value.And;
import org.jebol.domain.value.BitwiseOperation;

public class BitwiseAndFunction extends BitwiseFunction {

    @Override
    public String name() {
        return "and~";
    }

    @Override
    protected BitwiseOperation operation() {
        return new And();
    }
}
