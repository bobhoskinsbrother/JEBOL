package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

/**
 * A script asking to stop, by calling QUIT.
 *
 * <p>Not a {@link Raised}, so TRY and CATCH do not see it. A script that
 * says it is finished is finished; something that could swallow that would
 * make QUIT mean "probably stop", which is no use to the code that calls
 * it.
 *
 * <p>Kept apart from {@link Stopped} because the two are different events.
 * Stopped is the host overruling the script, and its value is an error
 * explaining why. This is the script finishing early on purpose, and its
 * value is whatever the script chose to hand back.
 */
public final class QuitRequested extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Value answer;

    public QuitRequested(Value answer) {
        super("the script called quit", null, false, false);
        this.answer = answer;
    }

    /** What the script asked to hand back. None unless QUIT/RETURN said otherwise. */
    public Value answer() {
        return answer;
    }
}
