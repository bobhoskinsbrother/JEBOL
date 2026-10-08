package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class IntegerDivideNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "integer-divide";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAllNumbers("dividend", "divisor");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                Arithmetic.wholeQuotient(arguments.get(0), arguments.get(1));
    }
}
