package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The pair datatype, read out of {@code src/core/t-pair.c}.
 *
 * <p>Written from the C and not from the Java beside it. Each group names the
 * function it was taken from, so a disagreement can be settled by reading that
 * function rather than by arguing about what a pair ought to do.
 *
 * <p>The one fact underneath nearly all of it: a pair's halves are
 * {@code REBD32}, which {@code reb-c.h} declares as a C {@code float}. Single
 * precision, about seven significant digits. That is why a large whole number
 * loses its low digits, why a half above 3.4e38 becomes infinite instead of
 * staying large, and why taking a half out of {@code 0.1x0.2} gives
 * 0.100000001490116. Nothing about the spelling {@code 40x40} suggests any of
 * it.
 *
 * <p>The second fact, which the first hides: the bit operations and the parity
 * questions round each half to a whole number, halves going up, because
 * {@code ROUND_TO_INT} is {@code (REBINT)(floor(d + 0.5))}. Truncating instead
 * agrees on every whole half and disagrees on every fraction, so it is the
 * wrong answer that looks right.
 */
class PairFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String FALSE = "#(false)";

    @Nested
    @DisplayName("REBD32: the halves are single precision")
    class SinglePrecisionHalves {

        @Test
        @DisplayName("a large whole number loses its low digits")
        void aLargeWholeNumberIsRounded() {
            // 2147483647 is not representable as a float, so the half holds
            // 2147483648 and molds to seven significant digits.
            assertThat(answerTo("mold 2147483647x2147483647"))
                    .isEqualTo("\"2.147484e9x2.147484e9\"");
        }

        @Test
        @DisplayName("two whole numbers a step apart land on one pair half once halved")
        void twoNeighboursBecomeOneHalf() {
            // Rebol's own assertion, and it only holds because of the
            // narrowing: 2147483647 halved is 1073741824, and the literal
            // 1073741823 is also 1073741824 once it is a pair half.
            assertThat(answerTo("equal? 2147483647x2147483647 / 2 1073741823x1073741823"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("mold 1073741823x1073741823"))
                    .isEqualTo("\"1.073742e9x1.073742e9\"");
        }

        @Test
        @DisplayName("a half above the float range becomes infinite rather than large")
        void aHalfOverflowsToInfinity() {
            // The ON point is around 3.4e38, which is where a float runs out.
            assertThat(answerTo("mold 3.4e38x1")).isEqualTo("\"3.4e38x1\"");
            assertThat(answerTo("mold 3.5e38x1")).isEqualTo("\"1.#INFx1\"");
            assertThat(answerTo("mold as-pair 1e300 -1e300")).isEqualTo("\"1.#INFx-1.#INF\"");
        }

        @Test
        @DisplayName("an infinite half is a value the pair keeps, not a failure")
        void anInfiniteHalfIsKept() {
            // A pair is the one datatype here that holds an infinity as a
            // matter of course, which is why constructing one must not refuse.
            assertThat(answerTo("pair? as-pair 1e300 -1e300")).isEqualTo(TRUE);
            assertThat(answerTo("p: as-pair 1e300 -1e300 decimal? p/1")).isEqualTo(TRUE);
            assertThat(answerTo("p: as-pair 1e300 -1e300 p/1 = 1.#INF")).isEqualTo(TRUE);
            assertThat(answerTo("p: as-pair 1e300 -1e300 p = p")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("taking a half out shows the single precision rounding")
        void aHalfReadsBackAtDoublePrecision() {
            // SET_DECIMAL widens the float to a double without recovering the
            // digits it never had, so the rounding becomes visible exactly
            // once: on the way out.
            assertThat(answerTo("mold first 0.1x0.2")).isEqualTo("\"0.100000001490116\"");
            assertThat(answerTo("mold 0.1x0.2")).isEqualTo("\"0.1x0.2\"");
        }

        @Test
        @DisplayName("a whole half molds with no decimal point, and a negative zero keeps its sign")
        void moldingTrimsTheDecimalPoint() {
            // DEC_MOLD_MINIMAL, and the sign is written from dtoa's own sign
            // flag rather than from the digits, so -0.0 molds as -0.
            assertThat(answerTo("mold 1x1")).isEqualTo("\"1x1\"");
            assertThat(answerTo("mold 1.5x3")).isEqualTo("\"1.5x3\"");
            assertThat(answerTo("mold -32767x-32767 % -32767")).isEqualTo("\"-0x-0\"");
            // And a negative zero half still equals a zero one, because
            // Cmp_Pair subtracts rather than comparing bits.
            assertThat(answerTo("equal? -32767x-32767 % -32767 0x0")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("REBTYPE(Pair): the binary actions")
    class Arithmetic {

        @Test
        @DisplayName("a pair, an integer, a decimal and a percent are the four accepted right sides")
        void theRightSideIsAPairOrAPlainNumber() {
            assertThat(answerTo("mold 1x2 + 1x2")).isEqualTo("\"2x4\"");
            assertThat(answerTo("mold 1x2 + 1")).isEqualTo("\"2x3\"");
            assertThat(answerTo("mold 1x2 + 1.5")).isEqualTo("\"2.5x3.5\"");
            assertThat(answerTo("mold 1x2 + 100%")).isEqualTo("\"2x3\"");
        }

        @Test
        @DisplayName("anything else is Trap_Math_Args")
        void anotherDatatypeIsRefused() {
            // The else branch of the type test, before the switch on the
            // action, so it catches every binary action at once.
            assertThat(errorIdOf("1x2 + \"a\"")).isNotEqualTo("no-error");
            assertThat(errorIdOf("1x2 + 0:0:1")).isNotEqualTo("no-error");
            assertThat(errorIdOf("1x2 + $1")).isNotEqualTo("no-error");
            assertThat(errorIdOf("1x2 * 'a")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("each half is added, subtracted and multiplied on its own")
        void eachHalfIsWorkedSeparately() {
            assertThat(answerTo("mold 4x4 - 1x2")).isEqualTo("\"3x2\"");
            assertThat(answerTo("mold 2x3 * 2")).isEqualTo("\"4x6\"");
            assertThat(answerTo("mold negate 4x4")).isEqualTo("\"-4x-4\"");
            assertThat(answerTo("mold absolute -4x-4")).isEqualTo("\"4x4\"");
            assertThat(answerTo("mold reverse 1x2")).isEqualTo("\"2x1\"");
        }

        @Test
        @DisplayName("divide and remainder guard both halves of the divisor")
        void aZeroHalfRaises() {
            // `if (x2 == 0 || y2 == 0) Trap0(RE_ZERO_DIVIDE);` -- either
            // half being zero stops the whole operation, so the x half of
            // `1x1 / 1x0` never divides even though it could have.
            assertThat(answerTo("mold 8x8 / 2")).isEqualTo("\"4x4\"");
            assertThat(errorIdOf("1x1 / 0")).isEqualTo("zero-divide");
            assertThat(errorIdOf("1x1 / 1x0")).isEqualTo("zero-divide");
            assertThat(errorIdOf("1x1 / 0x1")).isEqualTo("zero-divide");
            assertThat(errorIdOf("1x1 % 1x0")).isEqualTo("zero-divide");
        }

        @Test
        @DisplayName("remainder is fmod on each half")
        void remainderIsFmod() {
            assertThat(answerTo("mold 7x7 % 3")).isEqualTo("\"1x1\"");
            assertThat(answerTo("mold -32767x-32767 % -32767")).isEqualTo("\"-0x-0\"");
        }

        @Test
        @DisplayName("dividing by the most negative whole number still works")
        void theMostNegativeDivisor() {
            assertThat(answerTo("equal? -2147483648x-2147483648 / -2147483648 1x1"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("REBTYPE(Pair): AND, OR and XOR round each half first")
    class BitOperations {

        @Test
        @DisplayName("a whole number on the right applies to both halves")
        void aPlainNumberSpreadsAcrossBothHalves() {
            assertThat(answerTo("equal? 0x0 (1x1 and 0)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 1x1 (1x1 and 1)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 1x1 (1x1 or 0)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 1x1 (1x1 or 1)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 1x1 (1x1 xor 0)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 0x0 (1x1 xor 1)")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("two pairs are combined half by half")
        void twoPairsAreCombinedHalfByHalf() {
            assertThat(answerTo("equal? 1x0 (1x1 and 1x0)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 16x0 (16x16 and 16x4)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 7x4 (7x7 and 7x4)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 16x20 (16x16 or 16x4)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 7x7 (7x7 or 7x4)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 0x20 (16x16 xor 16x4)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 0x3 (7x7 xor 7x4)")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a fractional half is rounded to the nearest, not truncated")
        void aFractionalHalfIsRounded() {
            // ROUND_TO_INT(15.6) is 16, so `16x15.6 or 16x4` is 16x20.
            // Truncating gives 15, and 15 or 4 is 15, so the answer would be
            // 16x15 -- one off, and near enough right to survive review.
            assertThat(answerTo("equal? 1x1 (1.2x1 or 1)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 16x20 (16x15.6 or 16x4)")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 0x20 (16x15.9 xor 16x4)")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("A_ODDQ and A_EVENQ: parity rounds each half first")
    class Parity {

        @Test
        @DisplayName("odd needs both halves odd, and even needs both even")
        void bothHalvesHaveToAgree() {
            // `DECIDE(x && y)` for odd and `DECIDE(x == 0 && y == 0)` for
            // even, so one of each is neither odd nor even.
            assertThat(answerTo("odd? 1x1")).isEqualTo(TRUE);
            assertThat(answerTo("odd? 0x0")).isEqualTo(FALSE);
            assertThat(answerTo("odd? 0x1")).isEqualTo(FALSE);
            assertThat(answerTo("odd? 1x0")).isEqualTo(FALSE);
            assertThat(answerTo("odd? 2x2")).isEqualTo(FALSE);
            assertThat(answerTo("even? 0x0")).isEqualTo(TRUE);
            assertThat(answerTo("even? 2x2")).isEqualTo(TRUE);
            assertThat(answerTo("even? 0x1")).isEqualTo(FALSE);
            assertThat(answerTo("even? 1x0")).isEqualTo(FALSE);
            assertThat(answerTo("even? 1x1")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a fractional half is rounded before the question is asked")
        void aFractionalHalfIsRoundedFirst() {
            // VAL_PAIR_X_INT is ROUND_TO_INT, so 2.9 is odd because it
            // rounds to 3, and 1.9x2.1 is even because both round to 2.
            assertThat(answerTo("odd? 1.1x2.9")).isEqualTo(TRUE);
            assertThat(answerTo("odd? 1.1x2.2")).isEqualTo(FALSE);
            assertThat(answerTo("even? 1.9x2.1")).isEqualTo(TRUE);
            assertThat(answerTo("even? 1.1x2.2")).isEqualTo(FALSE);
        }
    }

    @Nested
    @DisplayName("PD_Pair: reading and writing through a path")
    class Paths {

        @Test
        @DisplayName("a half answers to its name and to its position")
        void twoSpellingsForEachHalf() {
            assertThat(answerTo("p: 1x2 mold p/x")).isEqualTo("\"1.0\"");
            assertThat(answerTo("p: 1x2 mold p/y")).isEqualTo("\"2.0\"");
            assertThat(answerTo("p: 1x2 mold p/1")).isEqualTo("\"1.0\"");
            assertThat(answerTo("p: 1x2 mold p/2")).isEqualTo("\"2.0\"");
        }

        @Test
        @DisplayName("a position outside one and two is refused")
        void aPositionOutsideTheTwoHalvesIsRefused() {
            // `if (n != 1 && n != 2) return PE_BAD_SELECT;` -- the ON points
            // are 1 and 2 and the OFF points are 0 and 3.
            assertThat(errorIdOf("p: 1x2 p/0")).isEqualTo("invalid-path");
            assertThat(errorIdOf("p: 1x2 p/3")).isEqualTo("invalid-path");
            assertThat(errorIdOf("p: 1x2 p/z")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("writing a half changes the pair the word holds")
        void writingAHalfChangesThePair() {
            // The C writes through the value in place. A pair that cannot be
            // written in place has to put a new one back in the word, and
            // either way p is 0x1 afterwards.
            assertThat(answerTo("p: 1x1 p/x: 0 mold p")).isEqualTo("\"0x1\"");
            assertThat(answerTo("p: 1x1 p/y: 0 mold p")).isEqualTo("\"1x0\"");
            assertThat(answerTo("p: 1x1 p/1: 5 mold p")).isEqualTo("\"5x1\"");
            assertThat(answerTo("p: 1x1 p/2: 5 mold p")).isEqualTo("\"1x5\"");
            assertThat(answerTo("p: 1x1 p/x: 0 equal? p 0x1")).isEqualTo(TRUE);
            assertThat(answerTo("p: 1x1 p/x: 0 p/y: 0 equal? p 0x0")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a decimal may be written into a half and nothing else may")
        void onlyANumberMayBeWritten() {
            // `if (IS_INTEGER(val)) ... else if (IS_DECIMAL(val)) ... else
            // return PE_BAD_SET;`
            assertThat(answerTo("p: 1x1 p/x: 1.5 mold p")).isEqualTo("\"1.5x1\"");
            assertThat(errorIdOf("p: 1x1 p/x: \"a\"")).isEqualTo("bad-path-set");
            assertThat(errorIdOf("p: 1x1 p/x: none")).isEqualTo("bad-path-set");
            assertThat(errorIdOf("p: 1x1 p/x: 1x1")).isEqualTo("bad-path-set");
        }

        @Test
        @DisplayName("p/area is the size of the rectangle, and never negative")
        void theAreaDropsTheSign() {
            // `SET_DECIMAL(pvs->store, fabsf(x * y))` -- multiplied and then
            // made positive, so the two negative cases below answer the same
            // as the positive ones.
            assertThat(answerTo("p: 10x20 p/area = 200.0")).isEqualTo(TRUE);
            assertThat(answerTo("p: -10x20 p/area = 200.0")).isEqualTo(TRUE);
            assertThat(answerTo("p: 1.5x3 p/area = 4.5")).isEqualTo(TRUE);
            assertThat(answerTo("p: 1.5x-3 p/area = 4.5")).isEqualTo(TRUE);
            assertThat(answerTo("p: 10x20 decimal? p/area")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("p/area cannot be written, because there is nothing to write to")
        void theAreaIsReadOnly() {
            // `if (pvs->setval) return PE_BAD_SET;` before the area is even
            // worked out.
            assertThat(errorIdOf("p: 10x20 p/area: 100")).isEqualTo("bad-path-set");
        }
    }

    @Nested
    @DisplayName("A_MAKE and A_TO: what a pair can be made from")
    class Making {

        @Test
        @DisplayName("a number gives both halves that number")
        void aNumberFillsBothHalves() {
            assertThat(answerTo("mold to pair! 5")).isEqualTo("\"5x5\"");
            assertThat(answerTo("mold to pair! 1.5")).isEqualTo("\"1.5x1.5\"");
        }

        @Test
        @DisplayName("a block of two numbers gives the two halves")
        void aBlockOfTwoNumbers() {
            assertThat(answerTo("mold to pair! [1 2]")).isEqualTo("\"1x2\"");
            assertThat(answerTo("mold to pair! [1.5 2]")).isEqualTo("\"1.5x2\"");
        }

        @Test
        @DisplayName("a block holding anything but two numbers is refused")
        void aBlockOfSomethingElseIsRefused() {
            // MT_Pair returns FALSE for a non-number, and the caller falls
            // through to Trap_Make.
            assertThat(errorIdOf("to-pair [,4]")).isNotEqualTo("no-error");
            assertThat(errorIdOf("to pair! [\"a\" \"b\"]")).isNotEqualTo("no-error");
        }

        @Test
        @DisplayName("a string is scanned as a pair literal")
        void aStringIsScanned() {
            assertThat(answerTo("mold to pair! \"1x2\"")).isEqualTo("\"1x2\"");
            assertThat(answerTo("mold to pair! \"-3x4\"")).isEqualTo("\"-3x4\"");
        }

        @Test
        @DisplayName("a pair makes itself")
        void aPairMakesItself() {
            assertThat(answerTo("mold to pair! 1x2")).isEqualTo("\"1x2\"");
        }
    }

    @Nested
    @DisplayName("A_RANDOM and Min_Max_Pair")
    class RandomAndExtremes {

        @Test
        @DisplayName("RANDOM of a pair gives a pair of whole numbers")
        void randomGivesWholeHalves() {
            // `Random_Range((REBINT)x1, ...)` on each half, so the answer is
            // whole and within the half it came from.
            assertThat(answerTo("pair? random 10x20")).isEqualTo(TRUE);
            assertThat(answerTo("p: random 10x20 all [p/1 >= 1 p/1 <= 10 p/2 >= 1 p/2 <= 20]"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("RANDOM of an infinite half still answers a finite pair")
        void randomOfAnInfiniteHalfIsFinite() {
            // The C casts the half to a machine integer before randomising,
            // which turns an infinity into a number. Rebol's own assertion
            // only asks that the answer is finite.
            assertThat(answerTo("p: as-pair 1e300 -1e300 pair? p: random p")).isEqualTo(TRUE);
            assertThat(answerTo("p: random as-pair 1e300 -1e300 p/1 < 1.#INF"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("p: random as-pair 1e300 -1e300 p/2 > -1.#INF"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("MIN and MAX take each half separately")
        void extremesAreTakenHalfByHalf() {
            assertThat(answerTo("equal? 1x1 min 100x1 1x100")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 100x100 max 100x1 1x100")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("NEGATIVE? and POSITIVE? need both halves to agree")
        void bothHalvesDecideTheSign() {
            assertThat(answerTo("negative? -1x-1")).isEqualTo(TRUE);
            assertThat(answerTo("negative? -1x1")).isEqualTo(FALSE);
            assertThat(answerTo("negative? -1x0")).isEqualTo(FALSE);
            assertThat(answerTo("positive? 1x1")).isEqualTo(TRUE);
            assertThat(answerTo("positive? -1x1")).isEqualTo(FALSE);
            assertThat(answerTo("positive? -1x0")).isEqualTo(FALSE);
        }
    }

    @Nested
    @DisplayName("Cmp_Pair: ordered on the first half, tie broken on the second")
    class Ordering {

        @Test
        @DisplayName("equality needs both halves")
        void equalityNeedsBothHalves() {
            assertThat(answerTo("equal? 1x1 1x1")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 1x1 1.0x1.0")).isEqualTo(TRUE);
            assertThat(answerTo("not-equal? 1x1 1x0")).isEqualTo(TRUE);
            assertThat(answerTo("not-equal? 1x1 0x1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the first half settles it, and the second only breaks a tie")
        void theFirstHalfDecides() {
            // `if ((diff = x1 - x2) == 0) diff = y1 - y2;` -- so `0x0 < 1x-1`
            // is true, which a both-halves rule answers false.
            assertThat(answerTo("1x1 < 2x2")).isEqualTo(TRUE);
            assertThat(answerTo("0x0 < 1x-1")).isEqualTo(TRUE);
            assertThat(answerTo("-1x1 < 0x0")).isEqualTo(TRUE);
            assertThat(answerTo("1x2 < 2x1")).isEqualTo(TRUE);
            assertThat(answerTo("1x1 < 1x2")).isEqualTo(TRUE);
            assertThat(answerTo("1x2 > 1x1")).isEqualTo(TRUE);
        }
    }
}
