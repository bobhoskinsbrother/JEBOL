package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * COMPOSE, tested against {@code Compose_Block} in {@code src/core/c-do.c}.
 *
 * <p>That walk has four rules and each is a branch in the loop: a paren
 * is evaluated, a block it answers is spread unless /ONLY, an unset it
 * answers is dropped, and /DEEP reaches into nested blocks and copies any
 * block it passes over.
 */
class ComposeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a paren is evaluated and everything else is left as written")
    void onlyParensAreEvaluated() {
        assertThat(answerTo("(compose [a (1 + 1) b]) = [a 2 b]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a block a paren answered is spread")
    void aBlockAnswerIsSpread() {
        assertThat(answerTo("(compose [a (reduce [1 2]) b]) = [a 1 2 b]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/ONLY keeps that block whole")
    void onlyStopsTheSpreading() {
        assertThat(answerTo("(compose/only [a (reduce [1 2]) b]) = [a [1 2] b]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an unset a paren answered is dropped")
    void anUnsetLeavesNothingBehind() {
        assertThat(answerTo("(compose [a (()) b]) = [a b]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without /DEEP a nested block is the one that was there")
    void theShallowWalkShares() {
        assertThat(answerTo("inner: [1] outer: reduce [inner] same? inner first compose outer"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/DEEP copies every block it passes over")
    void theDeepWalkCopies() {
        assertThat(answerTo(
                "inner: [1] outer: reduce [inner] not same? inner first compose/deep outer"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("/DEEP reaches a paren inside a nested block")
    void theDeepWalkRecurses() {
        assertThat(answerTo("(compose/deep [a [b (1 + 1)]]) = [a [b 2]]")).isEqualTo("#(true)");
        assertThat(answerTo("(compose [a [b (1 + 1)]]) = [a [b (1 + 1)]]"))
                .as("and without /DEEP it does not")
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a map composes as the pairs it holds")
    void aMapIsComposed() {
        assertThat(answerTo("m: make map! [a (1 + 1)] (select compose m 'a) = 2"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a paren in a map is never spread")
    void aMapNeverSpreads() {
        assertThat(answerTo(
                "m: make map! [a (reduce [1 2])] (select compose m 'a) = [1 2]"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a value that is not a block composes to itself")
    void aBareValueIsUnchanged() {
        assertThat(answerTo("(compose 5) = 5")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an empty block composes to an empty block")
    void theDegenerateBlock() {
        assertThat(answerTo("empty? compose []")).isEqualTo("#(true)");
        assertThat(answerTo("empty? compose/deep []")).isEqualTo("#(true)");
    }
}
