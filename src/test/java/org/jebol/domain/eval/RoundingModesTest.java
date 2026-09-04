package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ROUND's six modes, each measured against a real R3.
 *
 * <p>Specified in {@code spec/natives.allium}. They disagree in more
 * places than the names suggest, so each is checked at both halves and on
 * either side of one: /DOWN and /FLOOR agree on every positive and part
 * company on every negative, and /HALF-DOWN and /HALF-CEILING agree
 * everywhere except on an exact half.
 */
class RoundingModesTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String across(String mode) {
        return answerTo("mold reduce ["
                + "round" + mode + " 1.5 round" + mode + " 2.5 "
                + "round" + mode + " -1.5 round" + mode + " -2.5 "
                + "round" + mode + " 1.4 round" + mode + " 1.6 "
                + "round" + mode + " -1.4]");
    }

    @Test
    @DisplayName("the default takes a half away from zero")
    void theDefaultGoesAwayFromZero() {
        assertThat(across("")).isEqualTo("\"[2.0 3.0 -2.0 -3.0 1.0 2.0 -1.0]\"");
    }

    @Test
    @DisplayName("/down truncates towards zero")
    void downTruncates() {
        assertThat(across("/down")).isEqualTo("\"[1.0 2.0 -1.0 -2.0 1.0 1.0 -1.0]\"");
    }

    @Test
    @DisplayName("/floor goes towards negative, which /down does not")
    void floorIsNotDown() {
        assertThat(across("/floor")).isEqualTo("\"[1.0 2.0 -2.0 -3.0 1.0 1.0 -2.0]\"");
    }

    @Test
    @DisplayName("/ceiling goes towards positive")
    void ceilingGoesUp() {
        assertThat(across("/ceiling")).isEqualTo("\"[2.0 3.0 -1.0 -2.0 2.0 2.0 -1.0]\"");
    }

    @Test
    @DisplayName("/even sends a half to the even side")
    void evenGoesToEven() {
        assertThat(across("/even")).isEqualTo("\"[2.0 2.0 -2.0 -2.0 1.0 2.0 -1.0]\"");
    }

    @Test
    @DisplayName("/half-down sends a half towards zero and rounds the rest normally")
    void halfDownOnlyChangesHalves() {
        assertThat(across("/half-down")).isEqualTo("\"[1.0 2.0 -1.0 -2.0 1.0 2.0 -1.0]\"");
    }

    @Test
    @DisplayName("/half-ceiling sends a half towards positive")
    void halfCeilingOnlyChangesHalves() {
        assertThat(across("/half-ceiling"))
                .isEqualTo("\"[2.0 3.0 -1.0 -2.0 1.0 2.0 -1.0]\"");
    }

    @Test
    @DisplayName("/to is unaffected by the modes being added")
    void scaleStillWorks() {
        assertThat(answerTo("mold reduce [round/to 1.234 0.01 round/to 17 5]"))
                .isEqualTo("\"[1.23 15]\"");
    }
}
