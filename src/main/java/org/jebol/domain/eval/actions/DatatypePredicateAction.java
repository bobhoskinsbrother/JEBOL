package org.jebol.domain.eval.actions;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ActionValue;
import org.jebol.domain.value.DefaultNative;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class DatatypePredicateAction extends DefaultNative implements ActionValue {

    private final Datatype asked;

    public DatatypePredicateAction(Datatype asked) {
        this.asked = asked;
    }

    @Override
    public String nativeName() {
        return asked.spelling() + "?";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return acceptsAnyType("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(arguments.getFirst().datatype() == asked);
    }
}
