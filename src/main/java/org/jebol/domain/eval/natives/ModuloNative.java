package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ArithmeticNative;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ModuloNative extends ArithmeticNative {

    @Override
    public String nativeName() {
        return "modulo";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnythingDivisible("dividend", "divisor");
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("floor");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> Arithmetic.findTheRest(
                arguments.get(0), arguments.get(1),
                refinements.contains("floor")
                        ? Arithmetic.Division.SIGN_FOLLOWS_THE_DIVISOR
                        : Arithmetic.Division.NEVER_NEGATIVE);
    }
}
