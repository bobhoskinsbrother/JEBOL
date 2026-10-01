package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class GreatestCommonDivisorFunction extends WholeNumberFunction {

    @Override
    public String name() {
        return "gcd";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWholeNumbers("first", "second");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> IntegerValue.of(
                theGreatestDivisorSharedBy(wholeNumberOf(arguments.get(0)),
                        wholeNumberOf(arguments.get(1))));
    }
}
