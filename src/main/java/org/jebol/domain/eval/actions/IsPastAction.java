package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

public class IsPastAction extends SeriesOrFileAction {

    public IsPastAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "past?";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue port when port.isAFile() ->
                    LogicValue.of(findFileBehind(port, evaluator).isPastItsEnd());
            case RebolSeries series -> LogicValue.of(series.isPastEnd());
            case Value anythingElse -> refuseArgument(anythingElse, "series");
        };
    }
}
