package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.BitsetValue;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;
import java.util.Set;

public class WhetherComplementedFunction extends DefaultFunction {

    @Override
    public String name() {
        return "complement?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("value", Set.of(Datatype.BITSET)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> LogicValue.of(
                ((BitsetValue) arguments.getFirst()).isComplemented());
    }
}
