package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

public class IsHeadAction extends SeriesOrFileAction {

    public IsHeadAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "head?";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue port when port.isAFile() ->
                    LogicValue.of(findFileBehind(port, evaluator).isAtItsHead());
            case RebolSeries series -> LogicValue.of(series.atHead());
            case Value anythingElse -> refuseArgument(anythingElse, "series");
        };
    }
}
