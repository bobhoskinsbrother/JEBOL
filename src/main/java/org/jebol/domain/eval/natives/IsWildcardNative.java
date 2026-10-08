package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class IsWildcardNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "wildcard?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("path", Set.of(Datatype.FILE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                ((AnyStringValue) arguments.getFirst()).text().chars()
                        .anyMatch(letter -> letter == '*' || letter == '?'));
    }
}
