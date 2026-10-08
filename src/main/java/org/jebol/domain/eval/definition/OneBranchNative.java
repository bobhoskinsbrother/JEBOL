package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public abstract class OneBranchNative extends BranchingNative {

    protected abstract boolean runsTheBranchWhenTheConditionHolds();

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("condition", "branch");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                arguments.get(0).isTruthy() == runsTheBranchWhenTheConditionHolds()
                        ? branchTaken(arguments.get(1), evaluator, context, refinements)
                        : NoneValue.none();
    }
}
