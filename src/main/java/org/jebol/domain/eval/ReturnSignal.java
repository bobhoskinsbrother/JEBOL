package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

/**
 * RETURN or EXIT, travelling out of a function body.
 *
 * <p>Unwinds to the nearest function and no further, which is what separates
 * it from {@link ThrownSignal}: a helper that returns must not also return
 * from whatever called it.
 */
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
