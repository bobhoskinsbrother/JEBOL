package org.jebol.domain.eval.arithmetic;

import org.jebol.domain.value.Value;

public interface ArithmeticType {

    boolean shouldHandle(Value left, Value right, ArithmeticOperation operation);

    Value combine(Value left, Value right, ArithmeticOperation operation);

}
