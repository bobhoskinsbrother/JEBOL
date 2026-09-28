package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.TimeActions;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

public class Durations implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof TimeValue
                || right instanceof TimeValue
                && (operation.isCommutative() || aWholeNumberLosingATime(left, operation));
    }

    private boolean aWholeNumberLosingATime(Value left, ArithmeticOperation operation) {
        return operation.subtractsOneFromTheOther() && left instanceof IntegerValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof TimeValue span
                ? new TimeActions(span).combinedWith(right, operation)
                : new TimeActions((TimeValue) right).takenBy(left, operation);
    }

}
