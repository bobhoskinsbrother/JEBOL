package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;

public class ClampFunction extends DefaultFunction {

    @Override
    public String name() {
        return "clamp";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnythingWithARange("value", "minimum", "maximum");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value value = arguments.get(0);
            Value lowest = arguments.get(1);
            Value highest = arguments.get(2);
            refuseARangeOfAnotherDatatype(value, lowest, highest);
            return value.heldBetween(lowest, highest);
        };
    }

    private void refuseARangeOfAnotherDatatype(
            Value value, Value lowest, Value highest) {

        if (value.datatype() == lowest.datatype()
                && value.datatype() == highest.datatype()) {
            return;
        }
        throw Raised.of(EvaluationFailure.TYPE_MISMATCH,
                value.datatype().literalSpelling()
                        + " cannot be clamped between "
                        + lowest.datatype().literalSpelling() + " and "
                        + highest.datatype().literalSpelling());
    }
}
