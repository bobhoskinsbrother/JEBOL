package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public abstract class TrigonometryNative extends ArithmeticNative {

    protected abstract double ratioOf(double radians);

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsOnlyNumbers("value");
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("radians");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                DecimalValue.of(ratioOf(convertToRadians(arguments.getFirst(), refinements)));
    }

    private double convertToRadians(Value angle, Set<String> refinements) {
        double given = Comparison.asDouble(angle);
        return refinements.contains("radians") ? given : Math.toRadians(given);
    }
}
