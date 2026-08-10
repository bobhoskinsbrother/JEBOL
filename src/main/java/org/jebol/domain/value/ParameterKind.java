package org.jebol.domain.value;

/**
 * How a function parameter consumes its argument.
 *
 * <p>The two quoting kinds are named for how firmly they hold. They were
 * called {@code LITERAL} and {@code SOFT_LITERAL}, which left nothing to
 * say which sigil was which, and they were implemented the wrong way round
 * until a real R3 was asked. The sigil is now part of each name's javadoc.
 */
public enum ParameterKind {
    /** Evaluates the argument. */
    NORMAL,
    /**
     * Takes the argument exactly as written, written {@code :word} in a
     * spec. Nothing at the call site opts out: {@code f (add 1 2)} hands
     * the function the paren itself.
     */
    HARD_QUOTED,
    /**
     * Takes the argument as written, written {@code 'word} in a spec,
     * unless the call site is a paren, get-word or get-path, each of which
     * means "evaluate this one after all".
     */
    SOFT_QUOTED,
    /** A {@code /refinement} switch, contributing logic true or none. */
    REFINEMENT,
    /** An argument belonging to the preceding refinement. */
    REFINEMENT_ARGUMENT,
    /** A {@code return:} annotation, contributing no argument. */
    RETURN_TYPE
}
