package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.TupleActions;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

public class Tuples implements ArithmeticType {

    private final TupleActions actions;

    public Tuples() {
        actions = new TupleActions();
    }

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof TupleValue || right instanceof TupleValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return actions.combinedWith(left, right, operation);
    }
}
