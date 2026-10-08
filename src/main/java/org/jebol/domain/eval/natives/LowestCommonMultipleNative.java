package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class LowestCommonMultipleNative extends WholeNumberNative {

    @Override
    public String nativeName() {
        return "lcm";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWholeNumbers("first", "second");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            long first = wholeNumberOf(arguments.get(0));
            long second = wholeNumberOf(arguments.get(1));
            long divisor = theGreatestDivisorSharedBy(first, second);
            return IntegerValue.of(divisor == 0
                    ? 0
                    : Math.abs(first / divisor * second));
        };
    }
}
