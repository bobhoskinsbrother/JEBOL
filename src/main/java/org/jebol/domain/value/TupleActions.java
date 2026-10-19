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

    public Value combineWith(Value left, Value right, ArithmeticOperation operation) {
        return workOctetByOctet(left, right, (octet, against, fractional) ->
                combineAnOctet(octet, against, fractional, operation));
    }

    private long combineAnOctet(
            long octet, double against, boolean fractional,
            ArithmeticOperation operation) {

        return operation.onOctets(octet, against, fractional);
    }

    @FunctionalInterface
    public interface OctetWork {
        long against(long octet, double amount, boolean fractional);
    }

    public Value workOctetByOctet(Value left, Value right, OctetWork work) {
        refuseATimeBesideATuple(left, right);
        if (!(left instanceof TupleValue ours)) {
            throw Raised.cannotUse(left, "tuple arithmetic");
        }
        TupleValue theirs = right instanceof TupleValue tuple ? tuple : null;
        if (theirs == null && !isAPlainNumber(right)) {
            throw Raised.ofUnrelated(left, right);
        }
        int width = theirs == null
                ? ours.segmentCount()
                : Math.max(ours.segmentCount(), theirs.segmentCount());
        boolean fractional = right instanceof AnyDecimalValue;
        double amount = theirs == null ? Comparison.asDouble(right) : 0;

        int[] answer = new int[width];
        for (int at = 1; at <= width; at++) {
            long worked = work.against(ours.readTheOctetAt(at),
                    theirs == null ? amount : theirs.readTheOctetAt(at), fractional);
            answer[at - 1] = (int) Math.max(0, Math.min(255, worked));
        }
        return TupleValue.of(answer);
    }

    private boolean isAPlainNumber(Value right) {
        return right instanceof IntegerValue || right instanceof AnyDecimalValue;
    }

    private void refuseATimeBesideATuple(Value left, Value right) {
        if (left instanceof TimeValue || right instanceof TimeValue) {
            throw Raised.of(EvaluationFailure.NOT_RELATED,
                    TimeValue.TYPE,
                    TupleValue.TYPE);
        }
    }
}
