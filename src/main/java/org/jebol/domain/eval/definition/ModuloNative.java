package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ModuloNative extends DefaultNative {

    @Override
    public String name() {
        return "modulo";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnythingDivisible("dividend", "divisor");
    }

    @Override
    public Set<String> refinements() {
        return Set.of("floor");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> Arithmetic.rest(
                arguments.get(0), arguments.get(1),
                refinements.contains("floor")
                        ? Arithmetic.Division.SIGN_FOLLOWS_THE_DIVISOR
                        : Arithmetic.Division.NEVER_NEGATIVE);
    }
}
