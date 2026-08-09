package org.jebol.domain.eval;

import java.util.Optional;
import org.jebol.domain.value.ErrorValue;
import org.jebol.domain.value.Value;

/**
 * What evaluation produced: a value, or the error that stopped it.
 *
 * <p>Both are outcomes rather than one being an exception, because a REBOL
 * error is a value a script can catch. Nothing leaves the evaluator as a host
 * exception, including running out of nesting depth.
 */
public sealed interface Outcome {

    boolean completed();

    Optional<Value> value();

    Optional<ErrorValue> error();

    record Completed(Value result) implements Outcome {

        public Completed {
            if (result == null) {
                throw new IllegalArgumentException(
                        "a completed evaluation produced null; use UnsetValue.unset()");
            }
        }

        @Override
        public boolean completed() {
            return true;
        }

        @Override
        public Optional<Value> value() {
            return Optional.of(result);
        }

        @Override
        public Optional<ErrorValue> error() {
            return Optional.empty();
        }
    }

    record Raised(ErrorValue failure) implements Outcome {

        public Raised {
            if (failure == null) {
                throw new IllegalArgumentException("a raised evaluation needs an error");
            }
        }

        @Override
        public boolean completed() {
            return false;
        }

        @Override
        public Optional<Value> value() {
            return Optional.empty();
        }

        @Override
        public Optional<ErrorValue> error() {
            return Optional.of(failure);
        }
    }
}
