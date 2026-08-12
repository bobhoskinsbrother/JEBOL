package org.jebol.domain.eval;

/**
 * A script asking to stop and hand control back, by calling HALT.
 *
 * <p>Not {@link QuitRequested}, and the difference is the whole reason both
 * exist. QUIT ends the host's run; HALT ends the script and leaves the host
 * running, so a console that halts is still a console afterwards. R3's own
 * summary of it is "Stops evaluation and returns to the input prompt."
 *
 * <p>Not a {@link Raised} either, so TRY and CATCH do not see it. A script
 * that says it is stopping is stopping, and something that could swallow that
 * would make HALT mean "probably stop".
 *
 * <p>It carries no value. QUIT hands something back because a host may want
 * an exit status; halting is an interruption and there is nothing to hand
 * back, which is why HALT's spec takes no arguments.
 */
public final class HaltRequested extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HaltRequested() {
        super("the script called halt", null, false, false);
    }
}
