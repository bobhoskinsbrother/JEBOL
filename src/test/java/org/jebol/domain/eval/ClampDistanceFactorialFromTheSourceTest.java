package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * CLAMP, DISTANCE and FACTORIAL, the last three natives R3 has and JEBOL had
 * not.
 *
 * <p>All three are in {@code n-math.c} and none of them is in the 3.22.1
 * binary, so the C is the only authority here: the vendored source is ahead of
 * the build we can run. Every expectation below was read out of the C rather
 * than checked against a running Rebol, which is worth knowing when one of
 * them turns out to be wrong.
 *
 * <p>They were invisible until the surface collector was widened. Their specs
 * live in comments above the C functions rather than in {@code
 * boot/natives.reb}, so nothing that read the boot files alone could name
 * them, and the parity report said MISSING: 0 while all three were absent.
 *
 * <p>Specified in {@code spec/natives.allium}.
 */
class ClampDistanceFactorialFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("CLAMP holds a value inside a range")
    class TheClamping {

        @ParameterizedTest
        @CsvSource({
                "'clamp 5 1 3',   3",
                "'clamp 0 1 3',   1",
                "'clamp 2 1 3',   2",
                "'clamp 1 1 3',   1",
                "'clamp 3 1 3',   3",
                "'clamp 4 1 3',   3",
        })
        @DisplayName("a whole number, at every boundary and one step either side")
        void awholeNumber(String written, String expected) {
            assertThat(answerTo(written)).isEqualTo(expected);
        }

        @Test
        @DisplayName("a fraction, and a percent alongside it")
        void afraction() {
            assertThat(answerTo("clamp 5.5 1.0 3.0")).isEqualTo("3.0");
            assertThat(answerTo("clamp 0.5 1.0 3.0")).isEqualTo("1.0");
            assertThat(answerTo("clamp 2.5 1.0 3.0")).isEqualTo("2.5");
        }

        @Test
        @DisplayName("a pair, each half held on its own")
        void apair() {
            assertThat(answerTo("clamp 9x1 0x2 4x8"))
                    .as("across is clamped down and down is clamped up, in one call")
                    .isEqualTo("4x2");
        }

        @Test
        @DisplayName("a tuple, each octet on its own")
        void atuple() {
            assertThat(answerTo("clamp 200.100.50 0.0.0 128.128.128"))
                    .isEqualTo("128.100.50");
        }

        @Test
        @DisplayName("and an octet the bounds do not reach is clamped to nothing")
        void ashortBoundClampsToNothing() {
            // Surprising and it is what the C does: a missing octet in either
            // bound reads as zero, so a value longer than its bounds has the
            // rest of it clamped to zero rather than left alone.
            //
            // Written with a fourth octet because a tuple starts at three:
            // `128.128` is a decimal, not a two-part tuple, so the short bound
            // has to be short at the other end.
            assertThat(answerTo("clamp 200.100.50.40 0.0.0 128.128.128"))
                    .isEqualTo("128.100.50.0");
        }

        @Test
        @DisplayName("money, which is compared rather than clipped")
        void money() {
            assertThat(answerTo("clamp $5 $1 $3")).isEqualTo("$3");
            assertThat(answerTo("clamp $0 $1 $3")).isEqualTo("$1");
            assertThat(answerTo("clamp $2 $1 $3")).isEqualTo("$2");
        }

        @Test
        @DisplayName("bounds written the wrong way round answer the lower one")
        void reversedBoundsAnswerTheMinimum() {
            // `MAX(mini, MIN(maxi, val))` with the two swapped: the inner MIN
            // pulls the value down to the maximum, and the outer MAX pushes it
            // back up to the minimum. Not an error, and not a check anybody
            // wrote -- it falls out of the order the two are applied in.
            assertThat(answerTo("clamp 5 3 1")).isEqualTo("3");
        }

        @Test
        @DisplayName("a bound of a different type is refused rather than converted")
        void amixedBoundIsRefused() {
            assertThat(answerTo("error? try [clamp 5 1.0 3]")).isEqualTo("#(true)");
            assertThat(answerTo("error? try [clamp 5 1 3.0]")).isEqualTo("#(true)");
            assertThat(answerTo("error? try [clamp 1x1 0 4]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a whole number beyond thirty-two bits is still clamped")
        void alargeNumberIsNotTruncated() {
            // Where the C is plainly wrong rather than surprising. Its
            // `Clip_Int` takes a 32-bit int and CLAMP hands it a 64-bit one, so
            // a large value is truncated on the way in and comes back as
            // nonsense. Clamping is obviously meant to work across the integer
            // range, so this is what it means rather than what it does.
            assertThat(answerTo("clamp 9000000000 0 8000000000"))
                    .isEqualTo("8000000000");
            assertThat(answerTo("clamp 5000000000 0 8000000000"))
                    .isEqualTo("5000000000");
        }
    }

    @Nested
    @DisplayName("DISTANCE measures between two points")
    class TheDistance {

        @Test
        @DisplayName("as the crow flies")
        void asTheCrowFlies() {
            assertThat(answerTo("distance 0x0 3x4")).isEqualTo("5.0");
        }

        @Test
        @DisplayName("and the same however the two are ordered")
        void itisSymmetric() {
            assertThat(answerTo("distance 3x4 0x0")).isEqualTo("5.0");
        }

        @Test
        @DisplayName("/taxicab counts the streets instead")
        void taxicabCountsTheStreets() {
            assertThat(answerTo("distance/taxicab 0x0 3x4")).isEqualTo("7.0");
        }

        @Test
        @DisplayName("negative differences count the same as positive")
        void negativesCountTheSame() {
            assertThat(answerTo("distance/taxicab 3x4 0x0")).isEqualTo("7.0");
            assertThat(answerTo("distance 0x0 -3x-4")).isEqualTo("5.0");
        }

        @Test
        @DisplayName("a point from itself is nothing")
        void apointFromItselfIsNothing() {
            assertThat(answerTo("distance 7x7 7x7")).isEqualTo("0.0");
        }

        @Test
        @DisplayName("and the answer is a fraction, even when it is whole")
        void theanswerIsAFraction() {
            assertThat(answerTo("decimal? distance 0x0 3x4")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("anything that is not a pair is refused")
        void anonPairIsRefused() {
            assertThat(answerTo("error? try [distance 1 2]")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("FACTORIAL, exact while it can be")
    class TheFactorial {

        @ParameterizedTest
        @CsvSource({"0, 1", "1, 1", "2, 2", "5, 120", "10, 3628800"})
        @DisplayName("small ones are whole numbers")
        void smallOnesAreWhole(String given, String expected) {
            assertThat(answerTo("factorial " + given)).isEqualTo(expected);
        }

        @Test
        @DisplayName("twenty is the largest that fits a whole number")
        void twentyIsTheLargestWhole() {
            assertThat(answerTo("factorial 20")).isEqualTo("2432902008176640000");
            assertThat(answerTo("integer? factorial 20")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and twenty-one is a fraction, because it no longer fits")
        void twentyOneIsAFraction() {
            assertThat(answerTo("decimal? factorial 21")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a hundred and seventy still answers")
        void ahundredAndSeventyAnswers() {
            assertThat(answerTo("decimal? factorial 170")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a hundred and seventy-one is refused rather than infinity")
        void ahundredAndSeventyOneIsRefused() {
            // 170! is under a double's largest and 171! is over it, so the next
            // one would silently be infinity. Refused until there is a bignum.
            assertThat(answerTo("error? try [factorial 171]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a negative has no factorial and is refused")
        void anegativeIsRefused() {
            assertThat(answerTo("error? try [factorial -1]")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and both refusals say the value was out of range")
        void bothRefusalsNameTheRange() {
            assertThat(answerTo("e: try [factorial 171] e/id")).isEqualTo("out-of-range");
            assertThat(answerTo("e: try [factorial -1] e/id")).isEqualTo("out-of-range");
        }
    }
}
