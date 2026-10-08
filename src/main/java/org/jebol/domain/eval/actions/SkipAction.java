package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.util.List;

public class SkipAction extends SeriesOrFileAction {

    private static final boolean COUNTING_FROM_NOUGHT = false;

    public SkipAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "skip";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("series"),
                Parameter.required("offset", anOffset()));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value offset = arguments.get(1);
            return switch (arguments.getFirst()) {
                case PortValue port when port.isAFile() -> theFileBehind(port, evaluator)
                        .movedBy((long) Arithmetic.asMagnitude(offset));
                case RebolSeries series -> series.skipped(
                        series.positionNamedBy(offset, COUNTING_FROM_NOUGHT));
                case Value anythingElse -> refuseTheDatatype(anythingElse);
            };
        };
    }
}
