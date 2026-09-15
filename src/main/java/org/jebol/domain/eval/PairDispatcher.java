package org.jebol.domain.eval;

import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Slot;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

import java.util.Optional;

final class PairDispatcher implements Dispatcher {

    @Override
    public Value readFrom(Value target, Value selector) {
        PairValue pair = pairIn(target);
        Optional<Value> half = switch (selector) {
            case IntegerValue position -> pair.halfAt((int) position.magnitude());
            case WordValue name -> pair.half(name.canonical());
            default -> Optional.empty();
        };
        return half.orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                "a pair has an x half, a y half and an area, and nothing else"));
    }

    @Override
    public void writeTo(Slot place, Value selector, Value written) {
        place.setValue(withHalfWritten(pairIn(place.value()), selector, written));
    }

    private static PairValue withHalfWritten(
            PairValue pair, Value selector, Value written) {

        double replacement = switch (written) {
            case IntegerValue whole -> whole.magnitude();
            case DecimalValue quantity -> quantity.quantity();
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a pair half holds a number, not "
                            + written.datatype().literalSpelling());
        };
        return switch (selector) {
            case WordValue name when PairValue.isWritableHalf(name.canonical()) ->
                    pair.withHalf(name.canonical(), replacement);
            case IntegerValue position when position.magnitude() == 1
                    || position.magnitude() == 2 ->
                    pair.withHalfAt((int) position.magnitude(), replacement);
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET,
                    "a pair has an x half and a y half, and nothing else to write");
        };
    }

    private static PairValue pairIn(Value target) {
        if (target instanceof PairValue pair) {
            return pair;
        }
        throw Raised.of(EvaluationFailure.BAD_PATH_TYPE,
                target.datatype().literalSpelling());
    }
}
