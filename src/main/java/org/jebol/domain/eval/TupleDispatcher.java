package org.jebol.domain.eval;

import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Slot;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.Arrays;

final class TupleDispatcher implements Dispatcher {

    @Override
    public Value readFrom(Value target, Value selector) {
        TupleValue tuple = tupleIn(target);
        if (!(selector instanceof IntegerValue position)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    "cannot select " + selector.datatype().literalSpelling()
                            + " from " + target.datatype().literalSpelling());
        }
        long at = position.magnitude();
        return at < 1 || at > tuple.shownCount()
                ? NoneValue.none()
                : IntegerValue.of(tuple.octetAt((int) at));
    }

    @Override
    public void writeTo(Slot place, Value selector, Value written) {
        if (!(selector instanceof IntegerValue position)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH,
                    Molder.mold(selector));
        }
        place.setValue(withOctetWritten(
                tupleIn(place.value()), (int) position.magnitude(), written));
    }

    private static Value withOctetWritten(
            TupleValue tuple, int position, Value written) {

        if (position < 1 || position > TupleValue.MAXIMUM_SEGMENTS) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, Integer.toString(position));
        }
        if (written instanceof NoneValue) {
            int[] shortened = new int[position - 1];
            for (int at = 1; at < position; at++) {
                shortened[at - 1] = tuple.octetAt(at);
            }
            return TupleValue.of(shortened);
        }
        if (!(written instanceof IntegerValue) && !(written instanceof DecimalValue)) {
            throw Raised.of(EvaluationFailure.INVALID_PATH, Molder.mold(written));
        }
        long amount = written instanceof IntegerValue whole
                ? whole.magnitude()
                : (long) ((DecimalValue) written).quantity();
        int[] octets = tuple.octetsToTwelve();
        octets[position - 1] = (int) Math.max(0, Math.min(255, amount));
        int kept = position > tuple.shownCount() ? position : tuple.segmentCount();
        return TupleValue.of(Arrays.copyOf(octets, kept));
    }

    private static TupleValue tupleIn(Value target) {
        if (target instanceof TupleValue tuple) {
            return tuple;
        }
        throw Raised.of(EvaluationFailure.BAD_PATH_TYPE,
                target.datatype().literalSpelling());
    }
}
