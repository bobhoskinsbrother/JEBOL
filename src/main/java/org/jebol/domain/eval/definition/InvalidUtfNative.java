package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BinaryValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class InvalidUtfNative extends DefaultNative {

    @Override
    public String name() {
        return "invalid-utf?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("data", Set.of(Datatype.BINARY)),
                Parameter.belongingTo("utf", "num", Set.of(Datatype.INTEGER)));
    }

    @Override
    public Set<String> refinements() {
        return Set.of("utf");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                ((BinaryValue) arguments.getFirst()).theFirstMalformedUtf8()
                        .<Value>map(found -> found)
                        .orElseGet(NoneValue::none);
    }
}
