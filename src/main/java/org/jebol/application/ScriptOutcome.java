package org.jebol.application;

import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;

import java.time.Duration;
import java.util.Optional;

/**
 * What running a script produced, in terms a host can act on without knowing
 * anything about REBOL.
 *
 * @param conclusion why it stopped
 * @param value what it produced, or the error that stopped it
 * @param elapsed how long it took
 */
public record ScriptOutcome(Conclusion conclusion, Value value, Duration elapsed) {

    public ScriptOutcome {
        if (conclusion == null || value == null || elapsed == null) {
            throw new IllegalArgumentException("an outcome needs all three");
        }
    }

    public boolean succeeded() {
        return conclusion == Conclusion.PRODUCED_A_VALUE;
    }

    /** The value as a person would see it, or the error's message. */
    public String display() {
        return value instanceof ErrorValue error
                ? "** " + error.category().spelling() + " error: " + error.message()
                : Molder.mold(value);
    }

    /**
     * The value as the host sees it, where there is an obvious counterpart.
     * Anything without one comes back molded, so a host always gets something.
     */
    public Object asHostValue() {
        return HostValues.toHost(value);
    }

    /** The same, with REBOL's nothing arriving as absence rather than null. */
    public Optional<Object> asOptionalHostValue() {
        return HostValues.toOptionalHost(value);
    }

    /** The error's id, for a host deciding what to do about it. */
    public Optional<String> errorId() {
        return value instanceof ErrorValue error
                ? Optional.of(error.errorId())
                : Optional.empty();
    }
}
