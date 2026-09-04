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
        boolean closure) implements Value {

    public FunctionValue {
        if (spec == null || body == null || closedOver == null) {
            throw new IllegalArgumentException("a function needs a spec, a body and a context");
        }
        parameters = List.copyOf(parameters);
        localNames = List.copyOf(localNames);
    }

    public FunctionValue(
            BlockValue spec, BlockValue body, List<Parameter> parameters,
            List<String> localNames, Context closedOver) {
        this(spec, body, parameters, localNames, closedOver, false);
    }

    public FunctionValue asClosure() {
        return new FunctionValue(spec, body, parameters, localNames, closedOver, true);
    }

    public int arity() {
        return (int) parameters.stream().filter(Parameter::consumesAnArgument).count();
    }

    /**
     * A closure is its own datatype, not a function with a flag set.
     *
     * <p>{@code types.reb} gives closure! a row of its own, so
     * {@code type? :c} is {@code closure!} and {@code function? :c} is false
     * where {@code any-function? :c} is true. JEBOL answered function! for
     * both kinds, which made {@code closure?} false for every closure ever
     * written -- while MOLD said {@code make closure!}, because the molder
     * read the flag and nothing else did.
     */
    @Override
    public Datatype datatype() {
        return closure ? Datatype.CLOSURE : Datatype.FUNCTION;
    }

    @Override
    public String toString() {
        return "function/" + arity();
    }
}
