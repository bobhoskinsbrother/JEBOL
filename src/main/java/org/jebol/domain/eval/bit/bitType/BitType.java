package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.ValueHandler;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.Value;

public interface BitType extends ValueHandler {
    Value combine(Value left, Value right, BitwiseOperation operation);

    default long combinedBits(long left, long right, BitwiseOperation operation) {
        return operation.onWholeElements(left, right);
    }

}
