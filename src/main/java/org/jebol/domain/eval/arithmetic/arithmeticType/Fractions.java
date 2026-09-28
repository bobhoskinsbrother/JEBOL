package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

public class Fractions implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right, ArithmeticOperation operation) {
        return isAPlainNumber(left) && isAPlainNumber(right);
    }

    private boolean isAPlainNumber(Value value) {
        return value instanceof IntegerValue || value instanceof DecimalValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return operation.onFractions(Comparison.asDouble(left), Comparison.asDouble(right), true);
    }

}
