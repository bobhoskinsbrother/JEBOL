package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

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
            long bits = ((IntegerValue) arguments.get(0)).magnitude();
            long places = ((IntegerValue) arguments.get(1)).magnitude();
            return IntegerValue.of(places < 0 ? bits : moveBy(bits, places));
        };
    }
}
