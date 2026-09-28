package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.PairActions;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Value;

public class Points implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof PairValue
                || operation.isCommutative()
                && right instanceof PairValue
                && aPlainNumber(left);
    }

    private boolean aPlainNumber(Value value) {
        return value instanceof IntegerValue || value instanceof DecimalValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return new PairActions(left).combinedWith(right, operation);
    }

}
