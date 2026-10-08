package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;
import java.util.Set;

public class IsComplementedNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "complement?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value", Set.of(Datatype.BITSET)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                ((BitsetValue) arguments.getFirst()).isComplemented());
    }
}
