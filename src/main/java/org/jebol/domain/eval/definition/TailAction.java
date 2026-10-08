package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

public class TailAction extends SeriesOrFileAction {

    public TailAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "tail";
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case PortValue port when port.isAFile() ->
                    theFileBehind(port, evaluator).movedToTheEnd();
            case RebolSeries series -> series.tail();
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }
}
