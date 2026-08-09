package org.jebol.domain.eval;

import org.jebol.domain.value.ErrorValue;

/**
 * Internal control flow for an error travelling out of evaluation.
 *
 * <p>Never escapes the evaluator: it is caught at the boundary and handed back
 * as an {@code error!} value, because REBOL errors are values a script can
 * catch and a host exception is not.
 *
 * <p>Carries no stack trace. It is not a Java problem being reported, it is a
 * REBOL value being moved, and filling in a trace for every caught error would
 * cost more than it tells anyone.
 */
public final class Raised extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ErrorValue error;

    public Raised(ErrorValue error) {
        super(error.message(), null, false, false);
        this.error = error;
    }

    public ErrorValue error() {
        return error;
    }

    public static Raised of(EvaluationFailure failure) {
        return new Raised(ErrorValue.of(
                failure.category(), failure.errorId(), failure.description()));
    }

    public static Raised of(EvaluationFailure failure, String detail) {
        return new Raised(ErrorValue.of(
                failure.category(), failure.errorId(), failure.description() + ": " + detail));
    }
}
