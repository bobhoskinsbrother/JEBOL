package org.jebol.domain.value;

/** How a function parameter consumes its argument. */
public enum ParameterKind {
    /** Evaluates the argument. */
    NORMAL,
    /** Takes the argument unevaluated, written {@code 'word} in a spec. */
    LITERAL,
    /** Unevaluated unless the argument is a paren, get-word or get-path. */
    SOFT_LITERAL,
    /** A {@code /refinement} switch, contributing logic true or none. */
    REFINEMENT,
    /** An argument belonging to the preceding refinement. */
    REFINEMENT_ARGUMENT,
    /** A {@code return:} annotation, contributing no argument. */
    RETURN_TYPE
}
