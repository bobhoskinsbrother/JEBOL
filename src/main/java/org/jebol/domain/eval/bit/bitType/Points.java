package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Value;

public class Points implements BitType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof PairValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        PairValue ours = (PairValue) left;
        PairValue theirs = whatItCanTake(left, right);
        return PairValue.of(
                bitsOf(ours.x(), theirs.x(), operation),
                bitsOf(ours.y(), theirs.y(), operation));
    }

    private PairValue whatItCanTake(Value left, Value right) {
        return switch (right) {
            case PairValue point -> point;
            case IntegerValue(long magnitude) -> PairValue.of(magnitude, magnitude);
            default -> throw Arithmetic.notRelated(left, right);
        };
    }

    private long bitsOf(double ours, double theirs, BitwiseOperation operation) {
        return combinedBits(roundedHalfUp(ours), roundedHalfUp(theirs), operation);
    }

    private long roundedHalfUp(double half) {
        return (long) Math.floor(half + 0.5);
    }

}
