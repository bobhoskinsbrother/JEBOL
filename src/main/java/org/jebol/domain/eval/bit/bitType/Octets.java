package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.EvaluationFailure;
import org.jebol.domain.eval.Raised;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Value;

public class Octets implements BitType {

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof BinaryValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        return octetsCycledAgainstTheLonger(
                (BinaryValue) left, whatItCanTake(right), operation);
    }

    private BinaryValue whatItCanTake(Value right) {
        if (right instanceof BinaryValue octets) {
            return octets;
        }
        throw Raised.of(EvaluationFailure.INVALID_ARG, right);
    }

    private Value octetsCycledAgainstTheLonger(
            BinaryValue left, BinaryValue right, BitwiseOperation operation) {

        BinaryValue longer = left.lengthFromHere() >= right.lengthFromHere() ? left : right;
        BinaryValue shorter = longer == left ? right : left;
        int cycle = shorter.lengthFromHere();
        int[] combined = new int[longer.lengthFromHere()];
        for (int at = 0; at < combined.length; at++) {
            int theirs = cycle == 0 ? 0 : shorter.storage().at(shorter.index() + at % cycle);
            combined[at] = (int) combinedBits(
                    longer.storage().at(longer.index() + at), theirs, operation) & 0xFF;
        }
        return BinaryValue.of(combined);
    }

}
