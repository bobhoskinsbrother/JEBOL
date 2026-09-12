package org.jebol.domain.value;

import java.util.List;

/**
 * A function defined in REBOL, with the spec and body it was made from.
 *
 * <p>A closure is the same value with {@code closure} set: its call frame is
 * a real object rather than a function's own word list, so CONTEXT? of a
 * word bound into one answers that object where a function answers itself.
 */
public record FunctionValue(
        BlockValue spec,
        BlockValue body,
        List<Parameter> parameters,
        List<String> localNames,
        Context closedOver,
        boolean closure,
        Context declaredWords) implements Value {

    public FunctionValue {
        if (spec == null || body == null || closedOver == null) {
            throw new IllegalArgumentException("a function needs a spec, a body and a context");
        }
        parameters = List.copyOf(parameters);
        localNames = List.copyOf(localNames);
    }

    public FunctionValue(
            BlockValue spec, BlockValue body, List<Parameter> parameters,
            List<String> localNames, Context closedOver, boolean closure) {
        this(spec, body, parameters, localNames, closedOver, closure,
                Context.theWordsAFunctionDeclares());
    }

    public FunctionValue(
            BlockValue spec, BlockValue body, List<Parameter> parameters,
            List<String> localNames, Context closedOver) {
        this(spec, body, parameters, localNames, closedOver, false);
    }

    /**
     * The same function as a closure, sharing the words it declares.
     *
     * <p>Shared rather than fresh, because the body has already been bound to
     * them and a new set would leave every word pointing at a context nothing
     * ever lends a frame to.
     */
    public FunctionValue asClosure() {
        return new FunctionValue(spec, body, parameters, localNames, closedOver,
                true, declaredWords);
    }

    public int arity() {
        return (int) parameters.stream().filter(Parameter::consumesAnArgument).count();
    }

    @Override
    public Datatype datatype() {
        return closure ? Datatype.CLOSURE : Datatype.FUNCTION;
    }

    @Override
    public String toString() {
        return "function/" + arity();
    }
}
