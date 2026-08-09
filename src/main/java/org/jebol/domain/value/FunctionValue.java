package org.jebol.domain.value;

import java.util.List;

/** A function defined in REBOL, with the spec and body it was made from. */
public record FunctionValue(
        BlockValue spec,
        BlockValue body,
        List<Parameter> parameters,
        List<String> localNames,
        Context closedOver) implements Value {

    public FunctionValue {
        if (spec == null || body == null || closedOver == null) {
            throw new IllegalArgumentException("a function needs a spec, a body and a context");
        }
        parameters = List.copyOf(parameters);
        localNames = List.copyOf(localNames);
    }

    public int arity() {
        return (int) parameters.stream().filter(Parameter::consumesAnArgument).count();
    }

    @Override
    public Datatype datatype() {
        return Datatype.FUNCTION;
    }

    @Override
    public String toString() {
        return "function/" + arity();
    }
}
