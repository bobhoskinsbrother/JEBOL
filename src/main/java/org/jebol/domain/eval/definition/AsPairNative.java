package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class AsPairNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "as-pair";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsOnlyNumbers("x", "y");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> PairValue.of(
                Comparison.asDouble(arguments.get(0)), Comparison.asDouble(arguments.get(1)));
    }
}
