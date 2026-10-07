package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.util.List;

public class IsPastAction extends DefaultNative {

    @Override
    public String name() {
        return "past?";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsWhateverComesAlong("series");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case RebolSeries series -> LogicValue.of(series.isPastTheEnd());
            case Value anythingElse -> refuseTheArgument(anythingElse, "series");
        };
    }
}
