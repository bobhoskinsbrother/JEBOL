package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public abstract class InverseTrigonometryFunction extends DefaultFunction {

    protected abstract double radiansFor(double ratio);

    @Override
    public List<Parameter> parameters() {
        return acceptsOnlyNumbers("value");
    }

    @Override
    public Set<String> refinements() {
        return Set.of("radians");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            double angle = radiansFor(Comparison.asDouble(arguments.getFirst()));
            return DecimalValue.of(refinements.contains("radians")
                    ? angle
                    : Math.toDegrees(angle));
        };
    }
}
