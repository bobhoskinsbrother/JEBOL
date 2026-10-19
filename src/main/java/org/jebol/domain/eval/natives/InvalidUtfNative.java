package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class InvalidUtfNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "invalid-utf?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("data", Set.of(BinaryValue.TYPE)),
                Parameter.belongTo("utf", "num", Set.of(IntegerValue.TYPE)));
    }

    @Override
    public Set<String> refinementsDeclaredApart() {
        return Set.of("utf");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                ((BinaryValue) arguments.getFirst()).findTheFirstMalformedUtf8()
                        .<Value>map(found -> found)
                        .orElseGet(NoneValue::none);
    }
}
