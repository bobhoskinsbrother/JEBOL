package org.jebol.domain.value;

import java.util.List;
import java.util.Set;

/**
 * A built-in function.
 *
 * <p>Carries its interface but not its behaviour: the behaviour is looked up
 * by name where natives are implemented, so this package stays data and the
 * value model does not need to know how anything runs.
 */
public record NativeValue(
        String nativeName,
        List<Parameter> parameters,
        Set<String> declaredRefinements,
        Set<String> askedRefinements) implements Value {

    public NativeValue(String nativeName, List<Parameter> parameters) {
        this(nativeName, parameters, Set.of(), Set.of());
    }

    /**
     * The same native, recorded as having been asked for these refinements.
     *
     * <p>A refined native is not a different native, which is how it was
     * modelled: {@code copy/part} had its own registration. See
     * {@link org.jebol.domain.eval.RefinedCallable}.
     */
    public NativeValue askedFor(Set<String> refinements) {
        return new NativeValue(nativeName, parameters, declaredRefinements, refinements);
    }

    public boolean declares(String refinement) {
        return declaredRefinements.contains(refinement);
    }

    public NativeValue {
        if (nativeName == null || nativeName.isEmpty()) {
            throw new IllegalArgumentException("a native needs a name");
        }
        parameters = List.copyOf(parameters);
        declaredRefinements = Set.copyOf(declaredRefinements);
        askedRefinements = Set.copyOf(askedRefinements);
    }

    /**
     * How many values this call takes from the block.
     *
     * <p>A refinement may carry an argument of its own, and only when it
     * was asked for: {@code take} takes one value and {@code take/part}
     * takes two. Counting the refinement arguments unconditionally would
     * make every plain call demand values it has no use for.
     */
    public int arity() {
        long base = parameters.stream()
                .filter(parameter -> parameter.owningRefinement().isEmpty())
                .filter(Parameter::consumesAnArgument)
                .count();
        long forRefinements = parameters.stream()
                .filter(parameter -> parameter.owningRefinement()
                        .map(askedRefinements::contains).orElse(false))
                .filter(Parameter::consumesAnArgument)
                .count();
        return (int) (base + forRefinements);
    }

    /**
     * ACTION for the sixty Rebol declares as actions, NATIVE for the rest.
     *
     * <p>Read from the name, because the name is the only thing that carries
     * it: an action and a native are both host-language functions here and
     * look identical from every other angle. See {@link ActionNames}.
     */
    @Override
    public Datatype datatype() {
        return ActionNames.holds(nativeName) ? Datatype.ACTION : Datatype.NATIVE;
    }

    @Override
    public String toString() {
        return "native " + nativeName + "/" + arity();
    }
}
