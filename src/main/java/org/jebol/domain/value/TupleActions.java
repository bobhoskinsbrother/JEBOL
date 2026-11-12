package org.jebol.domain.value;

import org.jebol.domain.eval.Comparison;

public final class TupleActions {

    public TupleActions() {
    }

    public Value complement(Value left) {
        int[] octets = ((TupleValue) left).segments();
        for (int at = 0; at < octets.length; at++) {
            octets[at] = 255 - octets[at];
        }
        return TupleValue.of(octets);
    }

    public Value combineWith(TupleValue ours, Value right, ArithmeticOperation operation) {
        TupleValue theirs = right instanceof TupleValue tuple ? tuple : null;
        if (theirs == null && !isAPlainNumber(right)) {
            throw Raised.notRelatedToAction(ours, operation.spelling());
        }
        int width = theirs == null
                ? ours.segmentCount()
                : Math.max(ours.segmentCount(), theirs.segmentCount());
        boolean fractional = right instanceof AnyDecimalValue;
        double amount = theirs == null ? Comparison.asDouble(right) : 0;

        int[] answer = new int[width];
        for (int at = 1; at <= width; at++) {
            long worked = operation.onOctets(ours.readOctetAt(at),
                    theirs == null ? amount : theirs.readOctetAt(at), fractional);
            answer[at - 1] = (int) Math.max(0, Math.min(255, worked));
        }
        return TupleValue.of(answer);
    }

    private boolean isAPlainNumber(Value right) {
        return right instanceof IntegerValue || right instanceof AnyDecimalValue;
    }
}
