package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Decimals handed to things that want a whole number.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>The two rules go opposite ways in the same language, which is exactly
 * why both were checked rather than assumed. EVEN? and ODD? round half away
 * from zero, so 1.5 is even. A path index truncates towards zero, so
 * {@code b/1.6} is the first item.
 *
 * <p>The boundaries are the halves. Every whole decimal agrees under either
 * rule, so a wrong guess only shows up at .5.
 */
class DecimalsWhereWholeNumbersGoTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("EVEN? on a whole decimal asks about that number")
    void wholeDecimalsAreStraightforward() {
        assertThat(answerTo("mold reduce [even? 0.0 even? 1.0 even? 2.0 even? -2.0]"))
                .isEqualTo("\"[#(true) #(false) #(true) #(true)]\"");
    }

    @Test
    @DisplayName("EVEN? rounds a half away from zero before asking")
    void halvesRoundAwayFromZero() {
        assertThat(answerTo("mold reduce [even? 1.5 even? 2.5 even? 0.5]"))
                .isEqualTo("\"[#(true) #(false) #(false)]\"");
    }

    @Test
    @DisplayName("EVEN? rounds a negative half away from zero too")
    void negativeHalvesRoundAwayAsWell() {
        assertThat(answerTo("mold reduce [even? -1.5 even? -2.5]"))
                .isEqualTo("\"[#(true) #(false)]\"");
    }

    @Test
    @DisplayName("either side of a half falls the way rounding says")
    void thePointsEitherSideOfAHalf() {
        assertThat(answerTo("mold reduce [even? 1.4 even? 1.6 even? 2.4 even? 2.6]"))
                .isEqualTo("\"[#(false) #(true) #(true) #(false)]\"");
    }

    @Test
    @DisplayName("ODD? is the opposite answer throughout")
    void oddIsTheComplement() {
        assertThat(answerTo("mold reduce [odd? 1.0 odd? 1.5 odd? 2.5 odd? -1.5]"))
                .isEqualTo("\"[#(true) #(false) #(true) #(false)]\"");
    }

    @Test
    @DisplayName("a decimal too large to be anything but even is even")
    void theLargestDecimalIsEven() {
        assertThat(answerTo("even? 1.7976931348623157e308")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a decimal path index truncates rather than rounding")
    void aPathIndexTruncates() {
        assertThat(answerTo("b: [1 2] mold reduce [b/1.0 b/1.6 b/2.0 b/2.6]"))
                .as("1.6 truncates to 1, where EVEN? would have rounded it to 2")
                .isEqualTo("\"[1 1 2 2]\"");
    }

    @Test
    @DisplayName("TRY takes a paren as readily as a block")
    void tryAcceptsAParen() {
        assertThat(answerTo("try first [(1 + 1)]")).isEqualTo("2");
    }

    @Test
    @DisplayName("TRY on a paren still catches what it raises")
    void tryOnAParenStillCatches() {
        assertThat(answerTo("error? try first [(1 / 0)]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ATTEMPT takes a paren too")
    void attemptAcceptsAParen() {
        assertThat(answerTo("attempt first [(1 + 1)]")).isEqualTo("2");
        assertThat(answerTo("mold attempt first [(1 / 0)]")).isEqualTo("\"_\"");
    }
}
