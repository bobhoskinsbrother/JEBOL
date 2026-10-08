package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

public class BackAction extends SeriesOrFileAction {

    private static final int ONE_STEP = 1;

    public BackAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "back";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue port when port.isAFile() ->
                    theFileBehind(port, evaluator).movedBy(-ONE_STEP);
            case RebolSeries series -> series.atIndex(Math.max(1, series.index() - ONE_STEP));
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }
}
