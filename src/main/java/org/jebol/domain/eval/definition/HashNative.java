package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.eval.ValueHash;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class HashNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "hash";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value"));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                IntegerValue.of(new ValueHash(evaluator.symbols()).of(arguments.getFirst()));
    }
}
