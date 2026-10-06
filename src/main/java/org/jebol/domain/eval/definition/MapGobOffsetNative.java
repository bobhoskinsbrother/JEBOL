package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class MapGobOffsetNative extends GobMappingNative {

    @Override
    public String name() {
        return "map-gob-offset";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(
                Parameter.required("gob", Set.of(Datatype.GOB)),
                Parameter.required("xy", Set.of(Datatype.PAIR)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("reverse");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            GobValue from = (GobValue) arguments.get(0);
            PairValue point = (PairValue) arguments.get(1);
            return refinements.contains("reverse")
                    ? mappedOutwards(from, point)
                    : mappedInwards(from, point);
        };
    }

    private Value mappedOutwards(GobValue from, PairValue point) {
        GobValue reached = from;
        double addedX = point.x();
        double addedY = point.y();
        for (int depth = 0; depth < DEEPEST_GOB_WALK && reached.storage().parent() != null; depth++) {
            addedX += reached.storage().offset().x();
            addedY += reached.storage().offset().y();
            reached = new GobValue(reached.storage().parent(), 1);
        }
        return gobAndPoint(reached, PairValue.of(addedX, addedY));
    }
}
