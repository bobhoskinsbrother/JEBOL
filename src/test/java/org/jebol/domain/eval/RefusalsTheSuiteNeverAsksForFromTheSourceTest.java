package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Three things a real Rebol refuses and JEBOL quietly did.
 *
 * <p>Each answers rather than raising, which is the shape worth naming: a
 * caller cannot tell a refusal it never got from a result, so the script
 * carries on with a value nobody meant it to have.
 *
 * <p>None is reachable from Rebol's own suite. They were found by asking two
 * running interpreters the same questions, and every expectation here was read
 * off `./r3-head` before it was written down.
 */
class RefusalsTheSuiteNeverAsksForFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a series cannot be made with room for less than nothing")
    void anegativeSizeIsRefused() {
        assertThat(errorIdFrom("make block! -1")).isEqualTo("out-of-range");
        assertThat(errorIdFrom("make string! -1")).isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("but nothing is a size, and so is a positive one")
    void zeroAndPositiveAreSizes() {
        assertThat(errorIdFrom("make block! 0")).isEqualTo("no-error");
        assertThat(errorIdFrom("make block! 10")).isEqualTo("no-error");
    }

    /**
     * Only the whole-number form raises, which is worth pinning because it
     * looks like an inconsistency and is not: an integer scale of nothing is a
     * division, and a decimal one is a scale so small the answer rounds to the
     * nearest whole. `round/to 1.5 0` is 1 in a real 3.22.5 and `round/to 1
     * 0.0` is 1.0.
     */
    @Test
    @DisplayName("ROUND/TO nothing divides by nothing and says so")
    void roundingToNothingDividesByZero() {
        assertThat(errorIdFrom("round/to 1 0")).isEqualTo("zero-divide");
    }

    @Test
    @DisplayName("but a scale of no decimal is a scale, not a division")
    void adecimalScaleOfNothingIsNotAdivision() {
        assertThat(answerTo("round/to 1.5 0")).isEqualTo("1");
        assertThat(answerTo("round/to 1 0.0")).isEqualTo("1.0");
    }

    @Test
    @DisplayName("and rounding to something still works")
    void roundingToSomethingWorks() {
        assertThat(answerTo("round/to 11.65 0.1")).isEqualTo("11.7");
    }

    @Test
    @DisplayName("SELF cannot be assigned through a path")
    void selfCannotBeAssigned() {
        assertThat(errorIdFrom("o: make object! [a: 1] o/self: 2"))
                .as("R3 answers invalid-path, so the refusal is about the path "
                        + "rather than about the slot being protected")
                .isEqualTo("invalid-path");
    }

    @Test
    @DisplayName("and an ordinary field still can be")
    void anordinaryFieldStillCanBe() {
        assertThat(answerTo("o: make object! [a: 1] o/a: 2 o/a")).isEqualTo("2");
    }
}
