package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.date.DateArithmetic;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.DateValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.TimeValue;
import org.jebol.domain.value.Value;

public class Dates implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof DateValue
                || operation.isCommutative()
                && right instanceof DateValue
                && (left instanceof IntegerValue
                || left instanceof TimeValue && !operation.multiplies());
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof DateValue moment
                ? new DateArithmetic(moment).combinedWith(right, operation)
                : new DateArithmetic((DateValue) right).takenBy(left, operation);
    }


}
