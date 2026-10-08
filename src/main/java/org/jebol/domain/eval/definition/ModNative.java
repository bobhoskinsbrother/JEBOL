package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class ModNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "mod";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnythingDivisible("dividend", "divisor");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> Arithmetic.rest(
                arguments.get(0), arguments.get(1),
                Arithmetic.Division.SIGN_FOLLOWS_THE_DIVIDEND);
    }
}
