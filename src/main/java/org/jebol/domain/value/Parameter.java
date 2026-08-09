package org.jebol.domain.value;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * One parameter of a function's interface, derived from its spec block.
 *
 * <p>An empty {@code acceptedTypes} means the parameter accepts anything,
 * which is what a spec word with no type block means.
 */
public record Parameter(
        String name,
        ParameterKind kind,
        Set<Datatype> acceptedTypes,
        Optional<String> owningRefinement) {

    public Parameter {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("a parameter needs a name");
        }
        if (owningRefinement == null) {
            throw new IllegalArgumentException("owningRefinement is empty, never null");
        }
        if (kind == ParameterKind.REFINEMENT_ARGUMENT && owningRefinement.isEmpty()) {
            throw new IllegalArgumentException(
                    "a refinement argument must name the refinement it belongs to");
        }
        acceptedTypes = acceptedTypes.isEmpty()
                ? Set.of()
                : EnumSet.copyOf(acceptedTypes);
    }

    public static Parameter required(String name) {
        return new Parameter(name, ParameterKind.NORMAL, Set.of(), Optional.empty());
    }

    public static Parameter required(String name, Set<Datatype> acceptedTypes) {
        return new Parameter(name, ParameterKind.NORMAL, acceptedTypes, Optional.empty());
    }

    /** An argument taken unevaluated, written {@code 'word} in a spec. */
    public static Parameter literal(String name) {
        return new Parameter(name, ParameterKind.LITERAL, Set.of(), Optional.empty());
    }

    public static Parameter refinement(String name) {
        return new Parameter(name, ParameterKind.REFINEMENT, Set.of(), Optional.empty());
    }

    /** Whether this parameter takes a value from the block being evaluated. */
    public boolean consumesAnArgument() {
        return kind == ParameterKind.NORMAL
                || kind == ParameterKind.LITERAL
                || kind == ParameterKind.SOFT_LITERAL;
    }

    /** Whether a value of this datatype is acceptable here. */
    public boolean accepts(Datatype datatype) {
        return acceptedTypes.isEmpty() || acceptedTypes.contains(datatype);
    }
}
