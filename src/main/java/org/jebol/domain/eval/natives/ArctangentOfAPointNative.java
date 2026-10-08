package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ArctangentOfAPointNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "arctangent2";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("point", Set.of(Datatype.PAIR)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("radians");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                ((PairValue) arguments.getFirst())
                        .angleFromTheOrigin(refinements.contains("radians"));
    }
}
