package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class HashNative extends DefaultNative {

    @Override
    public String name() {
        return "hash";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("value"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                IntegerValue.of(Molder.mold(arguments.getFirst()).hashCode());
    }
}
