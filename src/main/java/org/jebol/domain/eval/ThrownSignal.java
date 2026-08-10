package org.jebol.domain.eval;

import java.util.Optional;
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
 *
 * <p>May carry a name, and then only a CATCH expecting that name takes it.
 * An unnamed CATCH is not a catch-all: a throw addressed to an outer
 * handler must travel past an inner one that was not expecting it, or the
 * naming would buy nothing.
 */
final class ThrownSignal extends RuntimeException {

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

    Value value() {
        return value;
    }

    /** The name it was thrown under, absent when it was thrown unnamed. */
    Optional<String> name() {
        return Optional.ofNullable(name);
    }
}
