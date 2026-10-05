package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.Arithmetic;
import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class AtzAction extends SeriesOrFileAction {

    private static final boolean COUNTING_FROM_NOUGHT = false;

    private static final int THE_HEAD_IS_ONE = 1;

    public AtzAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String name() {
        return "atz";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("series"),
                Parameter.required("position", Set.of(Datatype.INTEGER, Datatype.PAIR)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            Value position = arguments.get(1);
            return switch (arguments.getFirst()) {
                case PortValue port when port.isAFile() -> theFileBehind(port, evaluator)
                        .movedTo((long) Arithmetic.asMagnitude(position));
                case RebolSeries series -> series.atClamped(
                        series.positionNamedBy(position, COUNTING_FROM_NOUGHT) + THE_HEAD_IS_ONE);
                case Value anythingElse -> refuseTheArgument(anythingElse, "series");
            };
        };
    }
}
