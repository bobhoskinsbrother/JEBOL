package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.*;

import java.util.List;

public class IsNumberNative extends DefaultNative {

    @Override
    public String nativeName() {
        return "number?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.required("value",
                TypesetValue.ANY_TYPE.membersAnd(UnsetValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(isANumber(arguments.getFirst()));
    }

    private boolean isANumber(Value value) {
        return switch (value) {
            case AnyDecimalValue quantity -> !Double.isNaN(quantity.quantity());
            case IntegerValue whole -> true;
            case MoneyValue amount -> true;
            default -> false;
        };
    }
}
