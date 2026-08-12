package org.jebol.domain.eval;

import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;

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

    /**
     * The detail names what the failure was about, and an error reports
     * it as ARG1. It used to go only into the message, which meant ARG1
     * had to be read back out of prose -- and "division by zero" gave
     * the word DIVISION.
     */
    public static Raised of(EvaluationFailure failure, String detail) {
        // A word has to be called something, so a detail of nothing at
        // all becomes a string rather than a word. Building the word
        // regardless threw a host exception out of the very code that
        // was reporting a script error, which is the one thing an
        // interpreter must never do: `to word! ""` crashed rather than
        // raising, and the raise was already on its way.
        return new Raised(ErrorValue.about(
                failure.category(), failure.errorId(),
                failure.description() + ": " + detail,
                detail.isEmpty()
                        ? org.jebol.domain.value.StringValue.of(detail)
                        : WordValue.of(detail)));
    }

    /**
     * A failure naming two things, which several catalogue entries word with.
     *
     * <p>{@code expect-val: [{expected} :arg1 {not} :arg2]} is the example, and a
     * script reads both: Rebol's own gob test asserts {@code err/arg1 = set-word!}
     * rather than reading the datatype back out of the message.
     */
    public static Raised of(EvaluationFailure failure, Value first, Value second) {
        return new Raised(ErrorValue.about(
                failure.category(), failure.errorId(),
                failure.description() + ": " + Molder.mold(first)
                        + " and " + Molder.mold(second),
                first, second));
    }
}
