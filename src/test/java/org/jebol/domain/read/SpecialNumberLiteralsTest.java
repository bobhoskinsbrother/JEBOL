package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The infinities, NaN, and negative money.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>Two surprises here. Dividing by zero raises only when both sides are
 * whole numbers, so the decimal side follows the hardware and the integer
 * side does not. And the two equalities disagree about NaN in the opposite
 * direction to every other pair of values: the loose = says NaN equals
 * itself and the strict == says it does not.
 */
class SpecialNumberLiteralsTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        ScriptOutcome outcome = interpreter.run(source);
        assertThat(outcome.conclusion())
                .as("%s must not escape as a host exception", source)
                .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        return interpreter.display(outcome);
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("1.#INF reads and molds back as it was written")
    void infinityRoundTrips() {
        assertThat(answerTo("mold 1.#INF")).isEqualTo("\"1.#INF\"");
    }

    @Test
    @DisplayName("-1.#INF reads and molds back too")
    void negativeInfinityRoundTrips() {
        assertThat(answerTo("mold -1.#INF")).isEqualTo("\"-1.#INF\"");
    }

    @Test
    @DisplayName("1.#NaN reads and molds back")
    void notANumberRoundTrips() {
        assertThat(answerTo("mold 1.#NaN")).isEqualTo("\"1.#NaN\"");
    }

    @Test
    @DisplayName("all three are decimals")
    void theyAreDecimals() {
        assertThat(answerTo("mold reduce [type? 1.#INF type? 1.#NaN]"))
                .isEqualTo("\"[#(decimal!) #(decimal!)]\"");
    }

    @Test
    @DisplayName("whole-number division by zero still raises")
    void integerDivisionByZeroRaises() {
        assertThat(errorIdOf("1 / 0")).isEqualTo("zero-divide");
    }

    @Test
    @DisplayName("a decimal on either side gives infinity instead of raising")
    void decimalDivisionByZeroGivesInfinity() {
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
    void strictEqualityFollowsIeee() {
        assertThat(answerTo("1.#NaN == 1.#NaN")).isEqualTo("#(false)");
    }

    @Test
    @DisplayName("SAME? says NaN is itself")
    void sameSaysNaNIsItself() {
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
    void negativeMoneyWorks() {
        assertThat(answerTo("mold -$1")).isEqualTo("\"-$1\"");
        assertThat(answerTo("mold type? -$1")).isEqualTo("\"#(money!)\"");
        assertThat(answerTo("mold $1 + -$1")).isEqualTo("\"$0\"");
    }
}
