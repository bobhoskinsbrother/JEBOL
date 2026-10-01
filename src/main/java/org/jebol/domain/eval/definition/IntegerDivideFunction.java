package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class IntegerDivideFunction extends DefaultFunction {

    @Override
    public String name() {
        return "integer-divide";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAllNumbers("dividend", "divisor");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                Arithmetic.wholeQuotient(arguments.get(0), arguments.get(1));
    }
}
