package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

import java.util.Optional;

public final class ThrownSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Value value;
    private final transient Optional<String> name;

    public ThrownSignal(Value value) {
        this(value, Optional.empty());
    }

    public ThrownSignal(Value value, String name) {
        this(value, Optional.of(name));
    }

    private ThrownSignal(Value value, Optional<String> name) {
        super("throw", null, false, false);
        this.value = value;
        this.name = name;
    }

    public Value value() {
        return value;
    }

    public Optional<String> name() {
        return name;
    }
}
