package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class OneNumberNative extends ArithmeticNative {

    protected abstract double answerFor(double quantity);

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsOnlyNumbers("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> DecimalValue.of(
                answerFor(Comparison.asDouble(arguments.getFirst())));
    }
}
