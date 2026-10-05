package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class IndexzAction extends SeriesOrFileAction {

    private static final int COUNTING_FROM_NOUGHT = 0;

    private static final int THE_HEAD_IS_ONE = 1;

    public IndexzAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String name() {
        return "indexz?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("series", somewhereToStand()));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("xy");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> switch (arguments.getFirst()) {
            case ImageValue picture when refinements.contains("xy") ->
                    picture.whereItStands(COUNTING_FROM_NOUGHT);
            case PortValue port when port.isAFile() ->
                    IntegerValue.of(theFileBehind(port, evaluator).position());
            case RebolSeries series -> IntegerValue.of(series.index() - THE_HEAD_IS_ONE);
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }
}
