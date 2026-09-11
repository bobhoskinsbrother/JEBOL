package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * {@code else { DS_PUSH(value); if (ANY_BLOCK(value)) // Include PATHS
     * VAL_SERIES(DS_TOP) = Copy_Block(VAL_SERIES(value), 0); }} -- the C's own
     * comment names the case. A block and a map are rebuilt because that is
     * what descending into them means; every other block-shaped value is
     * copied, so two composes of one template share nothing.
     *
     * <p>Without the copy nothing shows until something binds one of the
     * answers, and then it reaches into the other through the shared path.
     */
    @Test
    @DisplayName("/DEEP copies a nested path rather than sharing it")
    void deepCopiesANestedPath() {
        assertThat(answerTo("""
                template: [x a/1 (1)]
                same? (pick compose/deep template 2) (pick compose/deep template 2)"""))
                .isEqualTo("#(false)");
    }

    /**
     * Rebol's own func-test, issue 217. Two functions made out of one
     * template: binding the second one's body reached into the first one's
     * through the shared path, and the first function stopped working the
     * moment the second was made.
     */
    @Test
    @DisplayName("which is what keeps two functions built from one template apart")
    void whichKeepsTwoFunctionsApart() {
        assertThat(answerTo("""
                f: func [c] [make function! reduce [copy [a] compose/deep [print a/1 (c)]]]
                f1: f [print 1]
                f2: f [print 2]
                e: try [f1 1] e/id""")).isEqualTo("bad-path-type");
    }

    @Test
    @DisplayName("and a block or a map is rebuilt rather than copied whole")
    void ablockOrMapIsRebuilt() {
        assertThat(answerTo("""
                template: [x [(1 + 1)] (1)]
                mold/flat compose/deep template""")).isEqualTo("\"[x [2] 1]\"");
        assertThat(answerTo("""
                template: [x [q] (1)]
                same? (pick compose/deep template 2) (pick compose/deep template 2)"""))
                .as("descending into a block gives a new block each time")
                .isEqualTo("#(false)");
    }
}
