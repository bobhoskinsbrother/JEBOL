package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

public class HeadAction extends SeriesOrFileAction {

    private static final long THE_FIRST_BYTE = 0;

    public HeadAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "head";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue port when port.isAFile() ->
                    theFileBehind(port, evaluator).movedTo(THE_FIRST_BYTE);
            case RebolSeries series -> series.head();
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }
}
