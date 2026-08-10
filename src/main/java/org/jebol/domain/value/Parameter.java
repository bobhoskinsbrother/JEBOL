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

    /**
     * An argument taken as written, written {@code 'word} in a spec.
     *
     * <p>This is what REBOL's own loop natives declare: {@code spec-of
     * :forall} reports {@code 'word [word!]}. Being soft rather than hard
     * is observable, because it lets the caller choose the counter at run
     * time: {@code chosen: 'n  foreach (chosen) [7] [n]} gives 7.
     */
    public static Parameter softQuoted(String name) {
        return new Parameter(name, ParameterKind.SOFT_QUOTED, Set.of(), Optional.empty());
    }

    /**
     * An argument that arrives only when its refinement was asked for.
     *
     * <p>{@code take} takes one value and {@code take/part} takes two, so
     * the count depends on the call site rather than on the native.
     */
    public static Parameter belongingTo(
            String refinement, String name, Set<Datatype> acceptedTypes) {
        return new Parameter(name, ParameterKind.REFINEMENT_ARGUMENT,
                acceptedTypes, Optional.of(refinement));
    }

    /**
     * An argument taken exactly as written, written {@code :word} in a
     * spec. Nothing at the call site opts out, which is what QUOTE needs:
     * a soft-quoted paren would evaluate, and QUOTE exists not to.
     */
    public static Parameter hardQuoted(String name) {
        return new Parameter(name, ParameterKind.HARD_QUOTED, Set.of(), Optional.empty());
    }

    public static Parameter refinement(String name) {
        return new Parameter(name, ParameterKind.REFINEMENT, Set.of(), Optional.empty());
    }

    /** Whether this parameter takes a value from the block being evaluated. */
    public boolean consumesAnArgument() {
        return kind == ParameterKind.NORMAL
                || kind == ParameterKind.HARD_QUOTED
                || kind == ParameterKind.SOFT_QUOTED
                || kind == ParameterKind.REFINEMENT_ARGUMENT;
    }

    /** Whether a value of this datatype is acceptable here. */
    public boolean accepts(Datatype datatype) {
        return acceptedTypes.isEmpty() || acceptedTypes.contains(datatype);
    }
}
