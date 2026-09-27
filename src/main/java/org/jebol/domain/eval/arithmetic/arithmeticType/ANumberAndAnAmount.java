package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.MoneyActions;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.Value;

public class ANumberAndAnAmount implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return right instanceof MoneyValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return new MoneyActions(MoneyValue.of(MoneyActions.asBigDecimal(left)))
                .combinedWith(right, operation);
    }

}
