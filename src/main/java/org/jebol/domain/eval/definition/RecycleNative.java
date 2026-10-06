package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.SeriesMemory;
import org.jebol.domain.value.UnsetValue;

import java.util.List;
import java.util.Set;

public class RecycleNative extends DefaultNative {

    @Override
    public String name() {
        return "recycle";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.belongingTo("ballast", "size", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("off", "on", "ballast", "torture", "pools");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> refinements.contains("off")
                ? UnsetValue.unset()
                : IntegerValue.of(SeriesMemory.collectNow());
    }
}
