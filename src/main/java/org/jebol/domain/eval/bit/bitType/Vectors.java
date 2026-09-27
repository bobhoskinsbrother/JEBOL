package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.VectorMath;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.VectorValue;

public class Vectors implements BitType {

    private static final String A_BIT_OPERATION_ON_A_VECTOR =
            "a bit operation on a vector";

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof VectorValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        refuseWhatItCannotTake(right);
        return VectorMath.done(left, right, operation);
    }

    private void refuseWhatItCannotTake(Value right) {
        if (!(right instanceof VectorValue) && !(right instanceof IntegerValue)) {
            throw Raised.cannotUse(right, A_BIT_OPERATION_ON_A_VECTOR);
        }
    }

}
