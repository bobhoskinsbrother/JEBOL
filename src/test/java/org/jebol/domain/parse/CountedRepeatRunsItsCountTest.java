package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A counted rule runs its count even when a round consumes nothing.
 *
 * <p>Specified in {@code spec/parse.allium}, confirmed against a real R3.
 *
 * <p>The open-ended repeats need a guard against a round that gets nowhere,
 * or {@code some [()]} never ends. A counted rule is bounded by the count
 * and cannot loop for ever, so the guard there buys nothing and costs the
 * rounds that were asked for.
 */
class CountedRepeatRunsItsCountTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a counted rule whose rounds consume nothing still runs them all")
    void aCountedRuleRunsEveryRound() {
        assertThat(answerTo(
                "x: 0 mold parse [1 2] [collect 2 [collect [] (x: x + 1) keep (x)]]"))
                .as("both rounds run although neither consumes any input")
                .isEqualTo("\"[[] 1 [] 2]\"");
    }

    @Test
    @DisplayName("the paren really ran twice")
    void theSideEffectHappenedEachRound() {
        assertThat(answerTo(
                "x: 0 parse [1 2] [collect 2 [collect [] (x: x + 1) keep (x)]] x"))
                .isEqualTo("2");
    }

    @Test
    @DisplayName("a counted rule that does consume input is unaffected")
    void anOrdinaryCountedRuleStillWorks() {
        assertThat(answerTo("mold parse [1 2 3] [collect 3 [keep skip]]"))
                .isEqualTo("\"[1 2 3]\"");
    }

    @Test
    @DisplayName("a count of one runs once")
    void aCountOfOneIsTheLowerBoundary() {
        assertThat(answerTo("mold parse [1 2] [collect 1 [keep skip] to end]"))
                .isEqualTo("\"[1]\"");
    }

    @Test
    @DisplayName("a count of zero runs nothing")
    void aCountOfZeroIsTheDegenerateCase() {
        assertThat(answerTo("mold parse [1 2] [collect 0 [keep skip] to end]"))
                .isEqualTo("\"[]\"");
    }

    @Test
    @DisplayName("a count range still runs up to its upper bound")
    void aCountRangeStillWorks() {
        assertThat(answerTo("mold parse [1 2 3] [collect 2 4 [keep skip]]"))
                .isEqualTo("\"[1 2 3]\"");
    }

    @Test
    @DisplayName("SOME still stops when a round gets nowhere")
    void theOpenEndedGuardIsUntouched() {
        // Without this guard the parse would never finish.
        assertThat(answerTo("parse \"\" [some [remove \"a\"]]")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("SOME with REMOVE still treats a shorter input as progress")
    void removingCountsAsProgress() {
        assertThat(answerTo("parse s: \"aa\" [some [remove \"a\"]] s")).isEqualTo("\"\"");
    }
}
