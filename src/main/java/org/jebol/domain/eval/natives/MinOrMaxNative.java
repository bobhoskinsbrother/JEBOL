package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;

public abstract class MinOrMaxNative extends DefaultNative {

    protected abstract boolean wantsLarger();

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("value1", "value2");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                chooseOneWanted(arguments.get(0), arguments.get(1));
    }

    private Value chooseOneWanted(Value left, Value right) {
        if (left instanceof PairValue(double ourX, double ourY)
                && right instanceof PairValue(double theirX, double theirY)) {
            return PairValue.of(
                    chooseHalfWanted(ourX, theirX), chooseHalfWanted(ourY, theirY));
        }
        boolean theFirstIsAtLeastTheSecond =
                Comparison.holds(left, right, Comparison.Strictness.GREATER_OR_EQUAL);
        return theFirstIsAtLeastTheSecond == wantsLarger() ? left : right;
    }

    private double chooseHalfWanted(double ours, double theirs) {
        return wantsLarger() ? Math.max(ours, theirs) : Math.min(ours, theirs);
    }
}
