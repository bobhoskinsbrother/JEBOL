package org.jebol.domain.eval;

/**
 * A script being stopped from outside: a deadline passed, or a host asked.
 *
 * <p>Not a {@link Raised}. A script cannot catch this, because a deadline a
 * script could ignore is not a deadline. It travels out past every TRY and
 * every CATCH to the boundary, which turns it into an outcome.
 */
public final class Stopped extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String reason;

    public Stopped(String reason) {
        super(reason, null, false, false);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
