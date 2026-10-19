package org.jebol.domain.read;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialNumberLiteralsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_VALUE);
        return interpreter.display(outcome);
    }

    private static String readTheErrorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("1.#INF reads and molds back as it was written")
    void roundTripInfinity() {
        assertThat(answerTo("mold 1.#INF")).isEqualTo("\"1.#INF\"");
    }

    @Test
    @DisplayName("-1.#INF reads and molds back too")
    void roundTripNegativeInfinity() {
        assertThat(answerTo("mold -1.#INF")).isEqualTo("\"-1.#INF\"");
    }

    @Test
    @DisplayName("1.#NaN reads and molds back")
    void roundTripNotANumber() {
        assertThat(answerTo("mold 1.#NaN")).isEqualTo("\"1.#NaN\"");
    }

    @Test
    @DisplayName("all three are decimals")
    void areDecimals() {
        assertThat(answerTo("mold reduce [type? 1.#INF type? 1.#NaN]"))
                .isEqualTo("\"[#(decimal!) #(decimal!)]\"");
    }

    @Test
    @DisplayName("whole-number division by zero still raises")
    void raiseOnIntegerDivisionByZero() {
        assertThat(readTheErrorIdOf("1 / 0")).isEqualTo("zero-divide");
    }

    @Test
    @DisplayName("a decimal on either side gives infinity instead of raising")
    void comeFromDecimalDivisionByZero() {
        assertThat(answerTo("mold 1.0 / 0")).isEqualTo("\"1.#INF\"");
        assertThat(answerTo("mold 1 / 0.0")).isEqualTo("\"1.#INF\"");
    }

    @Test
    @DisplayName("zero over zero as decimals gives NaN")
    void zeroOverZeroGivesNotANumber() {
        assertThat(answerTo("mold 0.0 / 0.0")).isEqualTo("\"1.#NaN\"");
    }

    @Test
    @DisplayName("the loose = says NaN equals itself")
    void looseEqualitySaysNaNIsItself() {
        assertThat(answerTo("1.#NaN = 1.#NaN")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the strict == follows the hardware and says it does not")
    void followIeeeInStrictEquality() {
        assertThat(answerTo("1.#NaN == 1.#NaN")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("SAME? says NaN is itself")
    void countNaNAsTheSameAsItself() {
        assertThat(answerTo("same? 1.#NaN 1.#NaN")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("comparing against NaN is true rather than false")
    void comparisonAgainstNaNHolds() {
        assertThat(answerTo("1.#NaN < 1")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the infinities compare equal to themselves")
    void infinitiesAreEqualToThemselves() {
        assertThat(answerTo("1.#INF = 1.#INF")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("negative money reads, molds and adds back to zero")
    void includeNegativeMoney() {
        assertThat(answerTo("mold -$1")).isEqualTo("\"-$1\"");
        assertThat(answerTo("mold type? -$1")).isEqualTo("\"#(money!)\"");
        assertThat(answerTo("mold $1 + -$1")).isEqualTo("\"$0\"");
    }
}
