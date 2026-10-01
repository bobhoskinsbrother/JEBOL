package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

import java.util.Optional;

public final class ThrownSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Value value;
    private final transient String name;

    ThrownSignal(Value value) {
        this(value, null);
    }

    ThrownSignal(Value value, String name) {
        super("throw", null, false, false);
        this.value = value;
        this.name = name;
    }

    public Value value() {
        return value;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }
}
