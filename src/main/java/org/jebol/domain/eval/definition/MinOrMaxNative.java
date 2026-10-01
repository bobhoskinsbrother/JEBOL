package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Comparison;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;

public abstract class MinOrMaxNative extends DefaultNative {

    protected abstract boolean wantsTheLarger();

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("value1", "value2");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                theOneWanted(arguments.get(0), arguments.get(1));
    }

    private Value theOneWanted(Value left, Value right) {
        if (left instanceof PairValue(double ourX, double ourY)
                && right instanceof PairValue(double theirX, double theirY)) {
            return PairValue.of(
                    theHalfWanted(ourX, theirX), theHalfWanted(ourY, theirY));
        }
        int order = Comparison.compareForSorting(left, right, false);
        return (wantsTheLarger() ? order >= 0 : order <= 0) ? left : right;
    }

    private double theHalfWanted(double ours, double theirs) {
        return wantsTheLarger() ? Math.max(ours, theirs) : Math.min(ours, theirs);
    }
}
