package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

sealed interface StepOutcome {

    record Produced(Value value) implements StepOutcome {

        public Produced {
            if (value == null) {
                throw new IllegalArgumentException(
                        "a produced step has a value; use waiting() when it has none");
            }
        }
    }

    record Waiting() implements StepOutcome {
    }

    StepOutcome WAITING = new Waiting();

    static StepOutcome of(Value value) {
        return new Produced(value);
    }

    static StepOutcome waiting() {
        return WAITING;
    }
}
