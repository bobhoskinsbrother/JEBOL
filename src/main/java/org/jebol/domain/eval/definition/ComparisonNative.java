package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.LogicValue;

public abstract class ComparisonNative extends DefaultNative {

    protected abstract Comparison.Strictness strictness();

    protected abstract boolean answersYesWhenItHolds();

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                answersYesWhenItHolds()
                        == Comparison.holds(arguments.get(0), arguments.get(1), strictness()));
    }
}
