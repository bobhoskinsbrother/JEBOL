package org.jebol.domain.eval.bit.bitType;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.TupleActions;
import org.jebol.domain.eval.arithmetic.BitwiseOperation;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

public class Tuples implements BitType {

    private final TupleActions actions;

    public Tuples() {
        actions = new TupleActions();
    }

    @Override
    public boolean shouldHandle(Value left, Value right) {
        return left instanceof TupleValue;
    }

    @Override
    public Value combine(Value left, Value right, BitwiseOperation operation) {
        refuseWhatItCannotTake(left, right);
        return actions.octetByOctet(left, right,
                (octet, against, ignoredFractionalFlag) ->
                        combinedBits(octet, (long) against, operation));
    }

    private void refuseWhatItCannotTake(Value left, Value right) {
        if (!(right instanceof TupleValue) && !(right instanceof IntegerValue)) {
            throw Arithmetic.notRelated(left, right);
        }
    }

}
