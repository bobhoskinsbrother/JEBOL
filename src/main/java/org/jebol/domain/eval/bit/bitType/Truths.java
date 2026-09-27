package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

public class Truths implements BitType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof LogicValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        return LogicValue.of(
                operation.onLogics(left.isTruthy(), whatItCanTake(right).isTruthy()));
    }

    private LogicValue whatItCanTake(Value right) {
        if (right instanceof LogicValue truth) {
            return truth;
        }
        throw Raised.of(EvaluationFailure.EXPECT_VAL,
                WordValue.of("logic!"),
                WordValue.of(right.datatype().literalSpelling()));
    }

}
