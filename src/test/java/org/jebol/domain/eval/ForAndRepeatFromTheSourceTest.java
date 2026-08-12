package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FOR and REPEAT from {@code evaluation-test.r3}. FOR over whole numbers traps
 * an overflow after the body has run; FOR walks a series from a start position;
 * and REPEAT over a pair walks a two-dimensional grid.
 */
class ForAndRepeatFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("FOR over whole numbers runs the body once, then overflows")
    void wholeNumberForRunsOnceThenOverflows() {
        assertThat(answerTo("""
                n: 0
                e: try [for i 9223372036854775807 9223372036854775807 1 [n: n + 1]]
                all [n = 1 e/id = 'overflow]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FOR steps whole numbers by the step, in both directions")
    void wholeNumberForSteps() {
        assertThat(answerTo("""
                out: copy [] for i 1 5 2 [append out i]
                out = [1 3 5]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FOR with a decimal bound counts by decimals")
    void decimalForCountsByDecimals() {
        assertThat(answerTo("""
                out: copy [] for i 1 3.5 1 [append out i]
                out = [1.0 2.0 3.0]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FOR walks a series from the start to the end index")
    void forWalksASeries() {
        assertThat(answerTo("""
                out: copy "" for x "abcde" 3 1 [append out x]
                out = "abcdebcdecde\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FOR walks a series to a series-position end")
    void forWalksASeriesToASeriesEnd() {
        assertThat(answerTo("""
                s: "abcde" out: copy "" for x s (at s 3) 1 [append out x]
                out = "abcdebcdecde\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("REPEAT over a pair walks a two-dimensional grid")
    void repeatOverAPairWalksAGrid() {
        assertThat(answerTo("""
                out: copy [] repeat x 2x2 [append out x]
                out = [1x1 2x1 1x2 2x2]""")).isEqualTo("#(true)");
    }
}
