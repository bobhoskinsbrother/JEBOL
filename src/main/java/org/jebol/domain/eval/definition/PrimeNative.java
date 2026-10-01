package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class PrimeNative extends WholeNumberNative {

    @Override
    public String name() {
        return "prime?";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWholeNumbers("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                hasNoDivisorBelowItsSquareRoot(wholeNumberOf(arguments.getFirst())));
    }

    private boolean hasNoDivisorBelowItsSquareRoot(long candidate) {
        if (candidate < 2) {
            return false;
        }
        for (long divisor = 2; divisor * divisor <= candidate; divisor++) {
            if (candidate % divisor == 0) {
                return false;
            }
        }
        return true;
    }
}
