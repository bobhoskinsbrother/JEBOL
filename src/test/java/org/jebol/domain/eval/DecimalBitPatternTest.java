package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A decimal and its bits, each convertible to the other.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>This is how a test suite pins floating point exactly, without writing
 * decimals whose text form has already rounded. Rebol's own decimal-test.r3
 * leans on it throughout.
 *
 * <p>The boundaries are the patterns that are not ordinary numbers: both
 * zeroes, both infinities, a NaN, and a binary shorter than the eight bytes
 * a double occupies.
 */
class DecimalBitPatternTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the bits of an ordinary decimal read back as that decimal")
    void anOrdinaryPatternRoundTrips() {
        assertThat(answerTo("mold to decimal! #{3FD3333333333333}")).isEqualTo("\"0.3\"");
    }

    @Test
    @DisplayName("a decimal converts to the bits it is made of")
    void aDecimalGivesItsBits() {
        assertThat(answerTo("mold to binary! 0.3")).isEqualTo("\"#{3FD3333333333333}\"");
    }

    @Test
    @DisplayName("the trip survives in both directions")
    void theTripRoundTrips() {
        assertThat(answerTo("mold to decimal! to binary! 0.3")).isEqualTo("\"0.3\"");
    }

    @Test
    @DisplayName("zero reads as zero")
    void zeroReadsAsZero() {
        assertThat(answerTo("mold to decimal! #{0000000000000000}")).isEqualTo("\"0.0\"");
    }

    @Test
    @DisplayName("negative zero keeps its sign")
    void negativeZeroKeepsItsSign() {
        assertThat(answerTo("mold to decimal! #{8000000000000000}")).isEqualTo("\"-0.0\"");
    }

    @Test
    @DisplayName("the two zeroes are the same number, differently signed")
    void theTwoZeroesDiffer() {
        assertThat(answerTo("z: to decimal! #{8000000000000000} 0.0 = z"))
                .isEqualTo("#(true)");
        assertThat(answerTo("z: to decimal! #{8000000000000000} 0.0 == z"))
                .isEqualTo("#(false)");
        assertThat(answerTo("z: to decimal! #{8000000000000000} same? 0.0 z"))
                .isEqualTo("#(false)");
        assertThat(answerTo("mold to binary! -0.0")).isEqualTo("\"#{8000000000000000}\"");
    }

    @Test
    @DisplayName("two NaNs answer the other way round from the two zeroes")
    void theNaNsAnswerTheOppositeWay() {
        assertThat(answerTo("1.#NaN = 1.#NaN")).isEqualTo("#(true)");
        assertThat(answerTo("1.#NaN == 1.#NaN")).isEqualTo("#(false)");
        assertThat(answerTo("same? 1.#NaN 1.#NaN")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the infinity patterns give the infinities")
    void theInfinitiesReadBack() {
        assertThat(answerTo("mold to decimal! #{7FF0000000000000}")).isEqualTo("\"1.#INF\"");
        assertThat(answerTo("mold to decimal! #{FFF0000000000000}")).isEqualTo("\"-1.#INF\"");
    }

    @Test
    @DisplayName("a NaN pattern gives a NaN")
    void aNaNPatternReadsBack() {
        assertThat(answerTo("mold to decimal! #{7FFFFFFFFFFFFFFF}")).isEqualTo("\"1.#NaN\"");
    }

    @Test
    @DisplayName("a short binary is read right-aligned")
    void aShortBinaryIsRightAligned() {
        assertThat(answerTo("mold to decimal! #{01}"))
                .isEqualTo("\"4.94065645841247e-324\"");
    }

    @Test
    @DisplayName("an empty binary is zero")
    void anEmptyBinaryIsTheDegenerateCase() {
        assertThat(answerTo("mold to decimal! #{}")).isEqualTo("\"0.0\"");
    }
}
