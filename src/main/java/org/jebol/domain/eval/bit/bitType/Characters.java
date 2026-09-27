package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

public class Characters implements BitType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof CharacterValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        CharacterValue ours = (CharacterValue) left;
        return CharacterValue.of((int) combinedBits(
                ours.codepoint(), whatItCanTake(left, right), operation));
    }

    private long whatItCanTake(Value left, Value right) {
        return switch (right) {
            case CharacterValue(int codepoint) -> codepoint;
            case IntegerValue(long magnitude) -> magnitude;
            default -> throw Arithmetic.notRelated(left, right);
        };
    }

}
