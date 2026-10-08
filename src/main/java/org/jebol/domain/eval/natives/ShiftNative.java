package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class ShiftNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "shift";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWholeNumbers("value", "places");
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("logical");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            long value = ((IntegerValue) arguments.get(0)).magnitude();
            long places = ((IntegerValue) arguments.get(1)).magnitude();
            return IntegerValue.of(refinements.contains("logical")
                    ? bitsShifted(value, places)
                    : shiftedKeepingTheSign(value, places));
        };
    }

    private long bitsShifted(long value, long places) {
        if (Math.abs(places) >= Long.SIZE) {
            return 0;
        }
        return places >= 0 ? value << places : value >>> -places;
    }

    private long shiftedKeepingTheSign(long value, long places) {
        if (places < 0) {
            long rightwards = -places;
            return rightwards >= Long.SIZE ? value >> (Long.SIZE - 1) : value >> rightwards;
        }
        if (places >= Long.SIZE) {
            if (value != 0) {
                throw Raised.of(EvaluationFailure.OVERFLOW,
                        "shifting " + value + " left by " + places + " loses every bit");
            }
            return 0;
        }
        long largestThatFits = Long.MIN_VALUE >>> places;
        long magnitude = value < 0 ? -value : value;
        if (Long.compareUnsigned(largestThatFits, magnitude) <= 0) {
            if (Long.compareUnsigned(largestThatFits, magnitude) < 0 || value >= 0) {
                throw Raised.of(EvaluationFailure.OVERFLOW,
                        "shifting " + value + " left by " + places + " leaves the range");
            }
            return Long.MIN_VALUE;
        }
        return value << places;
    }
}
