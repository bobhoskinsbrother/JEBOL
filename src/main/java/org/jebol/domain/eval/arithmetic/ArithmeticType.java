package org.jebol.domain.eval.arithmetic;

import org.jebol.domain.eval.ValueHandler;
import org.jebol.domain.value.Value;

public interface ArithmeticType extends ValueHandler {

    Value combine(Value left, Value right, ArithmeticOperation operation);

}
