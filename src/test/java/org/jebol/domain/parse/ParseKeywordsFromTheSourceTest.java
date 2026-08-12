package org.jebol.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Parse command words from {@code parse-test.r3}: NOT negates the rule after it
 * and consumes nothing, LIMIT is reserved and raises not-done, and TO or THRU
 * refuse a parse command word as a target with parse-rule.
 */
class ParseKeywordsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("NOT NOT collapses back to matching the rule")
    void doubleNotIsANoOp() {
        assertThat(answerTo("""
                parse "1" [not not "1" "1"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("NOT of a non-matching rule succeeds and consumes nothing")
    void notOfANonMatchSucceeds() {
        assertThat(answerTo("""
                parse "abc" [not "x" "abc"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("NOT of a matching rule fails the parse")
    void notOfAMatchFails() {
        assertThat(answerTo("""
                parse "abc" [not "a" "abc"]""")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("LIMIT is reserved and raises not-done")
    void limitRaisesNotDone() {
        assertThat(answerTo("""
                e: try [parse "123" [limit ["123"]]] e/id = 'not-done"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THRU refuses a parse command word as a target")
    void thruRefusesACommandWordTarget() {
        assertThat(answerTo("""
                e: try [parse "abc" [thru thru]] e/id = 'parse-rule"""))
                .isEqualTo("#(true)");
    }
}
