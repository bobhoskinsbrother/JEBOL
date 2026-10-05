package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class UtfNative extends DefaultNative {

    @Override
    public String name() {
        return "utf?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("data", Set.of(Datatype.BINARY)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                IntegerValue.of(((BinaryValue) arguments.getFirst()).byteOrderMark());
    }
}
