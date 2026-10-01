package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ToDegreesNative extends DefaultNative {

    @Override
    public String name() {
        return "to-degrees";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWholeNumbersAndDecimals("radians");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> DecimalValue.of(
                Math.toDegrees(Comparison.asDouble(arguments.getFirst())));
    }
}
