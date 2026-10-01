package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ToRadiansFunction extends DefaultFunction {

    @Override
    public String name() {
        return "to-radians";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWholeNumbersAndDecimals("degrees");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> DecimalValue.of(
                Math.toRadians(Comparison.asDouble(arguments.getFirst())));
    }
}
