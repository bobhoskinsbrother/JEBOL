package org.jebol.domain.eval;

import org.jebol.domain.value.*;

import java.util.Optional;

final class PairDispatcher implements Dispatcher {

    @Override
    public Value readFrom(Value target, Value selector) {
        PairValue pair = pairIn(target);
        Optional<Value> half = switch (selector) {
            case IntegerValue position -> pair.halfAt((int) position.magnitude());
            case AnyWordValue name -> pair.half(name.canonical());
            default -> Optional.empty();
        };
        return half.orElseThrow(() -> Raised.of(EvaluationFailure.INVALID_PATH,
                "a pair has an x half, a y half and an area, and nothing else"));
    }

    @Override
    public void writeTo(Slot place, Value selector, Value written) {
        place.setValue(withHalfWritten(pairIn(place.value()), selector, written));
    }

    PairValue withHalfWritten(PairValue pair, Value selector, Value written) {
        int half = theHalfNamedBy(selector);
        double replacement = switch (written) {
            case IntegerValue whole -> whole.magnitude();
            case AnyDecimalValue quantity -> quantity.quantity();
            default -> throw Raised.of(EvaluationFailure.BAD_PATH_SET);
        };
        return pair.withHalfAt(half, replacement);
    }

    private int theHalfNamedBy(Value selector) {
        return switch (selector) {
            case AnyWordValue name when name.canonical().equals("x") -> 1;
            case AnyWordValue name when name.canonical().equals("y") -> 2;
            case AnyWordValue name when name.canonical().equals("area") ->
                    throw Raised.of(EvaluationFailure.BAD_PATH_SET);
            case IntegerValue position when position.magnitude() == 1
                    || position.magnitude() == 2 -> (int) position.magnitude();
            default -> throw Raised.of(EvaluationFailure.INVALID_PATH);
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
