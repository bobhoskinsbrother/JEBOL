package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class ClampNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "clamp";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
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
