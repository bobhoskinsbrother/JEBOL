package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class FlushAction extends DefaultNative {

    @Override
    public String name() {
        return "flush";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("port", Set.of(Datatype.PORT)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            evaluator.output().flush();
            return arguments.getFirst();
        };
    }
}
