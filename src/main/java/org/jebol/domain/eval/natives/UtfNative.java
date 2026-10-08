package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class UtfNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "utf?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("data", Set.of(BinaryValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                IntegerValue.of(((BinaryValue) arguments.getFirst()).byteOrderMark());
    }
}
