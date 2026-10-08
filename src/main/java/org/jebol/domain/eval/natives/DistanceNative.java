package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class DistanceNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "distance";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(
                Parameter.required("value1", Set.of(PairValue.TYPE)),
                Parameter.required("value2", Set.of(PairValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("taxicab");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                ((PairValue) arguments.get(0)).apartFrom(
                        (PairValue) arguments.get(1),
                        refinements.contains("taxicab"));
    }
}
