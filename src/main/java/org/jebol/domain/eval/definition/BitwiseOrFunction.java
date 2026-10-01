package org.jebol.domain.eval.definition;

import org.jebol.domain.value.BitwiseOperation;
import org.jebol.domain.value.Or;

public class BitwiseOrFunction extends BitwiseFunction {

    @Override
    public String name() {
        return "or~";
    }

    @Override
    protected BitwiseOperation operation() {
        return new Or();
    }
}
