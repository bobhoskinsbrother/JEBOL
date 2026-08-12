package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parse RETURN from {@code parse-test.r3}. RETURN ends the parse at once and
 * answers a value: the value of a paren after it, or the slice the following
 * rule matched. A RETURN whose rule does not match fails the parse instead.
 */
class ParseReturnFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("RETURN of a paren answers the paren's value")
    void returnOfAParenAnswersItsValue() {
        assertThat(answerTo("""
                42 = parse [1 2 3] [return (42)]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("RETURN of a rule answers the slice it matched")
    void returnOfARuleAnswersTheMatchedSlice() {
        assertThat(answerTo("""
                [1 2] = parse [1 2 3] [return 2 skip]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("RETURN of a single rule answers a one-item slice")
    void returnOfASingleRuleAnswersOneItem() {
        assertThat(answerTo("""
                [a] = parse [a b c] [return skip]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("RETURN whose rule does not match fails the parse")
    void returnOfANonMatchFailsTheParse() {
        assertThat(answerTo("""
                not parse [1 2 3] [return "x"]""")).isEqualTo("#(true)");
    }
}
