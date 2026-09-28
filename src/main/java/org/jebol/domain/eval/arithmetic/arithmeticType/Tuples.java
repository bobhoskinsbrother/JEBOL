package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.TupleActions;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

public class Tuples implements ArithmeticType {

    private final TupleActions actions;

    public Tuples() {
        actions = new TupleActions();
    }

    @Override
    public boolean shouldHandle(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof TupleValue
                || operation.isCommutative()
                && right instanceof TupleValue
                && aPlainNumber(left);
    }

    private boolean aPlainNumber(Value value) {
        return value instanceof IntegerValue || value instanceof DecimalValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return left instanceof TupleValue
                ? actions.combinedWith(left, right, operation)
                : actions.combinedWith(right, left, operation);
    }
}
