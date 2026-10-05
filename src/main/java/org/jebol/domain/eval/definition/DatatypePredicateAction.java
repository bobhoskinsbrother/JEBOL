package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;

import java.util.List;

public class DatatypePredicateAction extends DefaultNative {

    private final Datatype asked;

    public DatatypePredicateAction(Datatype asked) {
        this.asked = asked;
    }

    @Override
    public String name() {
        return asked.spelling() + "?";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnyType("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(arguments.getFirst().datatype() == asked);
    }
}
