package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.SetWordValue;
import org.jebol.domain.value.Value;

import java.util.List;

public abstract class ShiftingNative extends ArithmeticNative {

    protected abstract long moveBy(long bits, long places);

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWholeNumbers("value", "bits");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            long bits = requireInteger(arguments.get(0));
            long places = requireInteger(arguments.get(1));
            return IntegerValue.of(places < 0 ? bits : moveBy(bits, places));
        };
    }

    private static long requireInteger(Value given) {
        if (given instanceof IntegerValue(long magnitude)) {
            return magnitude;
        }
        throw Raised.of(EvaluationFailure.EXPECT_VAL, SetWordValue.of(IntegerValue.TYPE.literalSpelling()), given);
    }
}
