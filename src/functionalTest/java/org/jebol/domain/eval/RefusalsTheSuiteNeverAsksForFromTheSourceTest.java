package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
