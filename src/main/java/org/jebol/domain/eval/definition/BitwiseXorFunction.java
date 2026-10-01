package org.jebol.domain.eval.definition;

import org.jebol.domain.value.BitwiseOperation;
import org.jebol.domain.value.Xor;

public class BitwiseXorFunction extends BitwiseFunction {

    @Override
    public String name() {
        return "xor~";
    }

    @Override
    protected BitwiseOperation operation() {
        return new Xor();
    }
}
