package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class IsHeadAction extends DefaultNative implements ActionValue {

    @Override
    public String nativeName() {
        return "head?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsWhateverComesAlong("series");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case RebolSeries series -> LogicValue.of(series.atHead());
            case Value anythingElse -> refuseTheArgument(anythingElse, "series");
        };
    }
}
