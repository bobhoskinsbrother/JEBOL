package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class ArctangentOfAPointFunction extends DefaultFunction {

    @Override
    public String name() {
        return "arctangent2";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("point", Set.of(Datatype.PAIR)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("radians");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                ((PairValue) arguments.getFirst())
                        .angleFromTheOrigin(refinements.contains("radians"));
    }
}
