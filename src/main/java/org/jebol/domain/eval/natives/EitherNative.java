package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class EitherNative extends BranchingNative {

    @Override
    public String nativeName() {
        return "either";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("condition", "true-branch", "false-branch");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> branchTaken(
                arguments.get(0).isTruthy() ? arguments.get(1) : arguments.get(2),
                evaluator, context, refinements);
    }
}
