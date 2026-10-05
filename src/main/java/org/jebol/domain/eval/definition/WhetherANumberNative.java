package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.DecimalValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.MoneyValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;
import org.jebol.domain.value.Value;

import java.util.List;

public class WhetherANumberNative extends DefaultNative {

    @Override
    public String name() {
        return "number?";
    }

    @Override
    public List<Parameter> parameters() {
        return List.of(Parameter.required("value",
                Typeset.ANY_TYPE.membersAnd(Datatype.UNSET)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(isANumber(arguments.getFirst()));
    }

    private boolean isANumber(Value value) {
        return switch (value) {
            case DecimalValue quantity -> !Double.isNaN(quantity.quantity());
            case IntegerValue whole -> true;
            case MoneyValue amount -> true;
            default -> false;
        };
    }
}
