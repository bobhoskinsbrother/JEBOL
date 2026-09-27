package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.CharacterValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Value;

public class WholeNumbers implements BitType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof IntegerValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        IntegerValue ours = (IntegerValue) left;
        return IntegerValue.of(
                combinedBits(ours.magnitude(), whatItCanTake(left, right), operation));
    }

    private long whatItCanTake(Value left, Value right) {
        return switch (right) {
            case IntegerValue(long magnitude) -> magnitude;
            case CharacterValue(int codepoint) -> codepoint;
            default -> throw Arithmetic.notRelated(left, right);
        };
    }

}
