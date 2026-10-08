package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class RecycleNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "recycle";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.belongingTo("ballast", "size", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("off", "on", "ballast", "torture", "pools");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> refinements.contains("off")
                ? UnsetValue.unset()
                : IntegerValue.of(SeriesMemory.collectNow());
    }
}
