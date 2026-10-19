package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class IsNegativeNative extends ArithmeticNative {

    @Override
    public String nativeName() {
        return "negative?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAllNumbers("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                arguments.getFirst() instanceof PairValue pair
                        ? pair.holdsForBothHalves(half -> half < 0)
                        : Comparison.asDouble(arguments.getFirst()) < 0);
    }
}
