package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class ClampNative extends ArithmeticNative {

    @Override
    public String nativeName() {
        return "clamp";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnythingWithRange("value", "minimum", "maximum");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value value = arguments.get(0);
            Value lowest = arguments.get(1);
            Value highest = arguments.get(2);
            requireSameDatatype(value, lowest, highest);
            return value.holdBetween(lowest, highest);
        };
    }

    private void requireSameDatatype(
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
