package org.jebol.application;

import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;

import java.time.Duration;
import java.util.Optional;

public record ScriptOutcome(Conclusion conclusion, Value value, Duration elapsed) {

    public ScriptOutcome {
        if (conclusion == null || value == null || elapsed == null) {
            throw new IllegalArgumentException("an outcome needs all three");
        }
    }

    public boolean succeeded() {
        return conclusion == Conclusion.PRODUCED_VALUE;
    }

    public String display() {
        return value instanceof ErrorValue error
                ? "** " + error.category().spelling() + " error: " + error.message()
                : Molder.mold(value);
    }

    public Object asHostValue() {
        return HostValues.toHost(value);
    }

    public Optional<Object> asOptionalHostValue() {
        return HostValues.toOptionalHost(value);
    }

    public Optional<String> errorId() {
        return value instanceof ErrorValue error
                ? Optional.of(error.errorId())
                : Optional.empty();
    }
}
