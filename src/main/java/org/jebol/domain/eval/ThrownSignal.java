package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

/**
 * THROW, travelling out to the nearest CATCH.
 *
 * <p>Crosses as many functions and loops as it needs to, unlike
 * {@link ReturnSignal}, which stops at one function, and {@link LoopSignal},
 * which stops at one loop.
 *
 * <p>Deliberately not a {@link Raised}. A throw is a decision and an error is
 * a failure, so CATCH must not swallow errors and TRY must not swallow
 * throws, or each would quietly take the other's work.
 */
final class ThrownSignal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Value value;

    ThrownSignal(Value value) {
        super("throw", null, false, false);
        this.value = value;
    }

    Value value() {
        return value;
    }
}
