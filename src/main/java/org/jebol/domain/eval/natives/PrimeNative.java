package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class PrimeNative extends WholeNumberNative {

    @Override
    public String nativeName() {
        return "prime?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
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
