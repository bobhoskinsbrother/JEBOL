package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

public final class ReturnSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Value value;

    public ReturnSignal(Value value) {
        super("return", null, false, false);
        this.value = value;
    }

    public Value value() {
        return value;
    }
}
