package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class LerpFunction extends DefaultFunction {

    @Override
    public String name() {
        return "lerp";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("value1", "value2", "fraction");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.get(0).partWayTo(arguments.get(1),
                        heldInsideTheJourney(
                                Comparison.asDouble(arguments.get(2))));
    }

    private double heldInsideTheJourney(double asked) {
        return Math.max(0, Math.min(1, asked));
    }
}
