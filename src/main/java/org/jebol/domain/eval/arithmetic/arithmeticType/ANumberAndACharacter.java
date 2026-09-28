package org.jebol.domain.eval.arithmetic.arithmeticType;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.arithmetic.ArithmeticOperation;
import org.jebol.domain.eval.arithmetic.ArithmeticType;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

public class ANumberAndACharacter implements ArithmeticType {

    @Override
    public boolean shouldHandle(Value left, Value right, ArithmeticOperation operation) {
        return right instanceof CharacterValue
                && (left instanceof IntegerValue || left instanceof DecimalValue);
    }

    @Override
    public Value combine(Value left, Value right, ArithmeticOperation operation) {
        CharacterValue letter = (CharacterValue) right;
        Value asNumber = left instanceof IntegerValue
                ? IntegerValue.of(letter.codepoint())
                : DecimalValue.of(letter.codepoint());
        Value plainer = left instanceof DecimalValue quantity
                ? DecimalValue.of(quantity.quantity())
                : left;
        return Arithmetic.combined(plainer, asNumber, operation);
    }


}
