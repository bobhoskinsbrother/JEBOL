package org.jebol.domain.eval.definition;

import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.value.LogicValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.Typeset;

import java.util.List;

public abstract class TypesetPredicateNative extends DefaultNative {

    protected abstract Typeset asked();

    @Override
    public String name() {
        return asked().spelling() + "?";
    }

    @Override
    public List<Parameter> parameters() {
        return acceptsAnyType("value");
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) ->
                LogicValue.of(asked().members().contains(arguments.getFirst().datatype()));
    }
}
