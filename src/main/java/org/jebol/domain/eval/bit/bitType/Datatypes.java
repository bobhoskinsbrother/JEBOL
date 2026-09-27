package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.DatatypeValue;
import org.jebol.domain.value.Value;

public class Datatypes implements BitType {

    private static final String A_BIT_OPERATION = "a bit operation";

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof DatatypeValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        throw Raised.cannotUse(left, A_BIT_OPERATION);
    }

}
