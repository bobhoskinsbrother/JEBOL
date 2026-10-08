package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.util.List;

public class AtAction extends SeriesOrFileAction {

    private static final boolean COUNTING_FROM_ONE = true;

    private static final int WHERE_IT_STANDS_IS_ONE = 1;

    public AtAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "at";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series"),
                Parameter.required("index", anOffset()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value index = arguments.get(1);
            return switch (arguments.getFirst()) {
                case PortValue port when port.isAFile() -> theFileBehind(port, evaluator)
                        .movedTo((long) Arithmetic.asMagnitude(index) - WHERE_IT_STANDS_IS_ONE);
                case RebolSeries series -> series.atClamped(series.index()
                        + series.positionNamedBy(index, COUNTING_FROM_ONE)
                        - WHERE_IT_STANDS_IS_ONE);
                case Value anythingElse -> refuseTheDatatype(anythingElse);
            };
        };
    }
}
