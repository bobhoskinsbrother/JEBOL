package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.StringValue;

import java.util.List;
import java.util.Set;

public class IsWildcardNative extends DefaultNative {

    @Override
    public String name() {
        return "wildcard?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("path", Set.of(Datatype.FILE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                ((StringValue) arguments.getFirst()).text().chars()
                        .anyMatch(letter -> letter == '*' || letter == '?'));
    }
}
