package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.CharacterActions;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.Value;

public class Characters implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof CharacterValue;
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        return new CharacterActions((CharacterValue) left).combinedWith(right, operation);
    }

}
