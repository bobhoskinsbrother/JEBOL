package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class IsPastAction extends DefaultNative {

    @Override
    public String nativeName() {
        return "past?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
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
