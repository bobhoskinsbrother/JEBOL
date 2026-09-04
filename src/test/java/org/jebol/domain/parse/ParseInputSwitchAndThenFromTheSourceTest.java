package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two string-parse behaviours from {@code parse-test.r3}: a get-word naming a
 * different series switches the parse input to it, and THEN consumes nothing
 * and succeeds so the rule after it runs straight on.
 */
class ParseInputSwitchAndThenFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a get-word naming another series switches the parse input")
    void getWordSwitchesTheInput() {
        assertThat(answerTo("""
                b: "this" parse "test" ["test" :b "this"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a get-word into the same series still seeks to that position")
    void getWordSeeksWithinTheSameSeries() {
        assertThat(answerTo("""
                parse "abc" [skip m: skip :m "bc"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THEN consumes nothing so the next rule runs straight on")
    void thenConsumesNothing() {
        assertThat(answerTo("""
                parse "ab" ["a" then "b"]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("THEN sits inside a longer sequence without breaking it")
    void thenInALongerSequence() {
        assertThat(answerTo("""
                parse "abc" [["a" then "b" "c"] | "x"]""")).isEqualTo("#(true)");
    }
}
