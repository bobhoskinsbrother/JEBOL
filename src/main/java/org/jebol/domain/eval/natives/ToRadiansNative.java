package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ToRadiansNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "to-radians";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWholeNumbersAndDecimals("degrees");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> DecimalValue.of(
                Math.toRadians(Comparison.asDouble(arguments.getFirst())));
    }
}
