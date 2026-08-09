package org.jebol.domain.eval;

import org.jebol.domain.value.Value;

/**
 * What taking one step produced: a value, or nothing yet.
 *
 * <p>Nothing yet is not the same as {@code unset!}. {@code unset!} is a value
 * a script can hold; this says the step started something that is still
 * waiting, so there is no value to hand on at all. A set-word waiting for what
 * follows it, a call still gathering arguments, and a function body just
 * pushed onto the frame stack all produce nothing yet.
 *
 * <p>This exists because those three used to be signalled by returning
 * {@code null}, in two different meanings, from two different methods. A
 * reader had to know which. Naming the thing costs one small type and stops
 * the question being asked.
 */
sealed interface StepOutcome {

    /** The step produced a value, ready to hand to whatever is waiting. */
    record Produced(Value value) implements StepOutcome {

        public Produced {
            if (value == null) {
                throw new IllegalArgumentException(
                        "a produced step has a value; use waiting() when it has none");
            }
        }
    }

    /** The step started something. Its value arrives later, or never. */
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
