package org.jebol.domain.eval;

/**
 * BREAK, travelling out of a loop body.
 *
 * <p>Internal control flow, like {@link Raised}, and caught by whichever loop
 * native is nearest. That answers the question left open in
 * {@code spec/natives.allium}: BREAK unwinds through the evaluator's own
 * control flow rather than through the error mechanism, and CATCH does not
 * intercept it.
 *
 * <p>Keeping the two apart matters. A loop is not a failure, so
 * {@code try [loop 3 [break]]} must not hand back an error, and a BREAK that
 * escapes its loop entirely is a mistake in its own right rather than
 * something a script should be able to catch as though it were one.
 *
 * <p>May carry a value, which is what the loop then answers. That is how a
 * search loop reports what it found without a variable outside the loop to
 * put it in. A plain BREAK carries nothing and the loop answers unset,
 * which is distinguishable from a BREAK/RETURN of none.
 */
final class LoopSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final LoopSignal BREAK = new LoopSignal(null);

    private final transient org.jebol.domain.value.Value answer;

    private LoopSignal(org.jebol.domain.value.Value answer) {
        super("break", null, false, false);
        this.answer = answer;
    }

    static LoopSignal breaking() {
        return BREAK;
    }

    /** A BREAK/RETURN, carrying what the loop should answer. */
    static LoopSignal breakingWith(org.jebol.domain.value.Value answer) {
        return new LoopSignal(answer);
    }

    /**
     * What the loop should answer, or unset for a plain BREAK.
     *
     * <p>Unset rather than none, because BREAK/RETURN NONE is a thing a
     * script can write and the two must not look the same.
     */
    org.jebol.domain.value.Value answer() {
        return answer == null
                ? org.jebol.domain.value.UnsetValue.unset()
                : answer;
    }
}
