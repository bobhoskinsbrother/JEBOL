package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Raised;
import org.jebol.domain.value.Value;

import java.util.List;

public class FactorialNative extends WholeNumberNative {

    private static final int LARGEST_EXACT_FACTORIAL = 20;

    private static final int LARGEST_FACTORIAL_AT_ALL = 170;

    @Override
    public String nativeName() {
        return "factorial";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWholeNumbers("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                theProductOfEveryWholeNumberUpTo(wholeNumberOf(arguments.getFirst()));
    }

    private Value theProductOfEveryWholeNumberUpTo(long value) {
        if (value < 0 || value > LARGEST_FACTORIAL_AT_ALL) {
            throw Raised.of(EvaluationFailure.OUT_OF_RANGE,
                    "factorial takes 0 to " + LARGEST_FACTORIAL_AT_ALL
                            + " and was given " + value);
        }
        if (value > LARGEST_EXACT_FACTORIAL) {
            double approximate = 1;
            for (long each = 2; each <= value; each++) {
                approximate *= each;
            }
            return DecimalValue.of(approximate);
        }
        long exact = 1;
        for (long each = 2; each <= value; each++) {
            exact *= each;
        }
        return IntegerValue.of(exact);
    }
}
