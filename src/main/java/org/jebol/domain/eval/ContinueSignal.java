package org.jebol.domain.eval;

/**
 * CONTINUE: stop this round of a loop and start the next.
 *
 * <p>Thrown rather than returned for the same reason {@link LoopSignal} is:
 * a CONTINUE may sit anywhere inside the body, including several blocks
 * deep, and every step between there and the loop would otherwise have to
 * carry the news back.
 *
 * <p>Its sibling stops the loop and this one stops only the round, so the
 * two are caught in different places: a break around the whole walk, a
 * continue around each turn of it.
 *
 * <p>No stack trace and no suppression, because it is control flow rather
 * than a fault and is thrown often.
 */
final class ContinueSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final ContinueSignal INSTANCE = new ContinueSignal();

    private ContinueSignal() {
        super("continue", null, false, false);
    }

    static ContinueSignal instance() {
        return INSTANCE;
    }
}
