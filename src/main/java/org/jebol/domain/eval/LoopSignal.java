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
 */
final class LoopSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final LoopSignal BREAK = new LoopSignal();

    private LoopSignal() {
        super("break", null, false, false);
    }

    static LoopSignal breaking() {
        return BREAK;
    }
}
