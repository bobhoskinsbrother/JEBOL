package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Running a script one expression at a time.
 *
 * <p>"The value of this source" and "the value of its first expression" are
 * different questions, and only {@link Interpreter#run} answered either. A
 * console answers the first for a line; anything reading a script written as
 * {@code assert-this that  and: then} needs the second, because the first
 * expression is the interesting one and the rest is ordinary code that still
 * has to run.
 */
class StepwiseRunTest {

    @Test
    @DisplayName("the first expression's value comes back, not the last")
    void answersTheFirstExpression() {
        Interpreter interpreter = Interpreter.create();
        Interpreter.Step step = interpreter.runNext("1 + 1  99");

        assertThat(interpreter.display(step.outcome())).isEqualTo("2");
    }

    @Test
    @DisplayName("and the source still unread comes with it")
    void answersWhatIsLeft() {
        Interpreter interpreter = Interpreter.create();
        Interpreter.Step step = interpreter.runNext("1 + 1  99");

        assertThat(interpreter.display(interpreter.run(step.rest()))).isEqualTo("99");
    }

    @Test
    @DisplayName("a source of one expression leaves nothing behind")
    void leavesNothingWhenThereIsOneExpression() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.runNext("1 + 1").rest()).isBlank();
    }

    @Test
    @DisplayName("an empty source is a step that did nothing")
    void anEmptySourceStepsNowhere() {
        Interpreter interpreter = Interpreter.create();
        Interpreter.Step step = interpreter.runNext("");

        assertThat(step.outcome().succeeded()).isTrue();
        assertThat(step.rest()).isBlank();
    }

    @Test
    @DisplayName("the words a step sets are still set for the next one")
    void wordsCarryFromStepToStep() {
        Interpreter interpreter = Interpreter.create();
        Interpreter.Step first = interpreter.runNext("counter: 7  counter");

        assertThat(interpreter.display(interpreter.run(first.rest()))).isEqualTo("7");
    }

    @Test
    @DisplayName("a failure in the first expression is a conclusion, not a throwable")
    void aFailureIsAnOutcome() {
        Interpreter interpreter = Interpreter.create();
        Interpreter.Step step = interpreter.runNext("1 / 0  99");

        assertThat(step.outcome().succeeded()).isFalse();
        assertThat(step.outcome().errorId()).contains("zero-divide");
    }

    @Test
    @DisplayName("what follows a failed expression is still handed back")
    void whatFollowsAFailureIsStillReturned() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.runNext("1 / 0  99").rest()).contains("99");
    }
}
