package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.PortValue;
import org.jebol.domain.value.RebolSeries;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class IndexAction extends SeriesOrFileAction {

    private static final int COUNTING_FROM_ONE = 1;

    public IndexAction(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String name() {
        return "index?";
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
            case NoneValue nothing -> nothing;
            case ImageValue picture when refinements.contains("xy") ->
                    picture.whereItStands(COUNTING_FROM_ONE);
            case PortValue port when port.isAFile() -> IntegerValue.of(
                    theFileBehind(port, evaluator).position() + COUNTING_FROM_ONE);
            case GobValue gob -> IntegerValue.of(gob.positionCountedAsUnsigned());
            case RebolSeries series -> IntegerValue.of(series.index());
            case Value anythingElse -> refuseTheDatatype(anythingElse);
        };
    }
}
