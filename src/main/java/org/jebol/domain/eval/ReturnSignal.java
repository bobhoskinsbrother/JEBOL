package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

final class ReturnSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Value value;

    ReturnSignal(Value value) {
        super("return", null, false, false);
        this.value = value;
    }

    Value value() {
        return value;
    }
}
