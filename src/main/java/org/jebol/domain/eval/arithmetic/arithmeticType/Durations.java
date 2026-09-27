package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.TimeActions;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

public class Durations implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof TimeValue || right instanceof TimeValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof TimeValue span
                ? new TimeActions(span).combinedWith(right, operation)
                : new TimeActions((TimeValue) right).takenBy(left, operation);
    }

}
