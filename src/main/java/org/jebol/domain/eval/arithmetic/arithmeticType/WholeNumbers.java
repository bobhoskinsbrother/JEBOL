package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

public class WholeNumbers implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof IntegerValue && right instanceof IntegerValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return integerCombined(((IntegerValue) left).magnitude(), ((IntegerValue) right).magnitude(), operation);
    }

    private Value integerCombined(long left, long right, ArithmeticOperation operation) {
        return operation.onWholeNumbers(left, right);
    }

}
