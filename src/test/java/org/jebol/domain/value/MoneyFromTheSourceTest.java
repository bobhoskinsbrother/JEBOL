package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The money datatype, read out of {@code src/core/t-money.c} and the
 * {@code deci} arithmetic in {@code src/core/f-deci.c}.
 *
 * <p>Written from the C and not from the Java beside it. Each group names the
 * function it was taken from, so a disagreement can be settled by reading that
 * function rather than by arguing about what a money ought to do.
 *
 * <p>A money is not a floating point number and it is not an unbounded
 * decimal either. {@code sys-deci.h} packs one into ninety-six bits: a sign
 * bit, an eight-bit signed power of ten, and a whole-number significand spread
 * across three fields totalling eighty-seven bits. So it carries exactly
 * twenty-six significant digits and its exponent runs from -128 to 127, and
 * arithmetic that leaves either bound raises rather than widening.
 *
 * <p>Ninety-six bits is also twelve bytes, which is why a money converts to
 * and from a binary at all, and why a shorter binary is padded from the left.
 */
class MoneyFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";
    private static final String NO_ERROR = "no-error";

    @Nested
    @DisplayName("A_MAKE and A_TO: the seven datatypes a money is made from")
    class Making {

        @Test
        @DisplayName("an integer becomes a whole money with no decimal places")
        void anIntegerMakesAWholeMoney() {
            assertThat(answerTo("mold to money! 1234")).isEqualTo("\"$1234\"");
            assertThat(answerTo("mold to money! 987")).isEqualTo("\"$987\"");
            assertThat(answerTo("m: to money! 987 mold to money! m * 12"))
                    .isEqualTo("\"$11844\"");
            assertThat(answerTo("mold to money! 0")).isEqualTo("\"$0\"");
            assertThat(answerTo("mold to money! -5")).isEqualTo("\"-$5\"");
        }

        @Test
        @DisplayName("a decimal and a percent both go through decimal_to_deci")
        void aDecimalAndAPercentMakeAMoney() {
            assertThat(answerTo("(to money! 1.5) = $1.5")).isEqualTo(TRUE);
            assertThat(answerTo("$0 = make money! 0%")).isEqualTo(TRUE);
            assertThat(answerTo("$1 = make money! 100%")).isEqualTo(TRUE);
            assertThat(answerTo("$100 = make money! make percent! $100")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a money makes itself")
        void aMoneyMakesItself() {
            assertThat(answerTo("$1 = to money! $1")).isEqualTo(TRUE);
            assertThat(answerTo("mold to money! $1.50")).isEqualTo("\"$1.50\"");
        }

        @Test
        @DisplayName("a string is scanned, and a string that does not scan is refused")
        void aStringIsScanned() {
            assertThat(answerTo("(to money! \"1.5\") = $1.5")).isEqualTo(TRUE);
            assertThat(errorIdOf("to money! \"abc\"")).isEqualTo("bad-make-arg");
            assertThat(errorIdOf("to money! \"\""))
                    .as("nothing at all is too-short and not a scan that failed. "
                            + "This asked for bad-make-arg because that is what "
                            + "JEBOL used to answer; a real Rebol tells the two "
                            + "apart, and now so does the money reader")
                    .isEqualTo("too-short");
        }

        @Test
        @DisplayName("a logic makes a money through MAKE, true being one and false nothing")
        void aLogicMakesAMoney() {
            assertThat(answerTo("$1 = make money! true")).isEqualTo(TRUE);
            assertThat(answerTo("$0 = make money! false")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an issue is refused, and the refusal is a decision rather than a gap")
        void anIssueIsRefused() {
            assertThat(errorIdOf("to money! #ff")).isEqualTo("bad-make-arg");
            assertThat(errorIdOf("to-money #0")).isEqualTo("bad-make-arg");
        }

        @Test
        @DisplayName("anything else is Trap_Make, so bad-make-arg")
        void anythingElseIsRefused() {
            assertThat(errorIdOf("to money! [1 2]")).isEqualTo("bad-make-arg");
            assertThat(errorIdOf("to money! 1x1")).isEqualTo("bad-make-arg");
            assertThat(errorIdOf("to money! 'a")).isEqualTo("bad-make-arg");
        }
    }

    @Nested
    @DisplayName("Bin_To_Money and deci_to_binary: the twelve byte form")
    class TheBinaryForm {

        @Test
        @DisplayName("twelve bytes are read as sign, exponent and significand")
        void twelveBytesAreRead() {
            assertThat(answerTo("$15 = make money! #{00000000000000000000000F}"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("$15 = to money! #{00000000000000000000000F}"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a shorter binary is padded from the left, not the right")
        void aShorterBinaryIsRightAligned() {
            assertThat(answerTo("$15 = make money! #{0F}")).isEqualTo(TRUE);
            assertThat(answerTo("$15 = to money! #{0F}")).isEqualTo(TRUE);
            assertThat(answerTo("$1 = to money! #{01}")).isEqualTo(TRUE);
            assertThat(answerTo("$0 = to money! #{}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a longer binary keeps its first twelve bytes")
        void alongerBinaryIsTruncated() {
            assertThat(answerTo("(to money! #{00000000000000000000000F}) "
                    + "= to money! #{00000000000000000000000FFF}")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the round trip through both conversions is exact")
        void theRoundTripIsExact() {
            assertThat(answerTo("#{0029B7D2DCC80CD2E3FFFFFF} "
                    + "= to-binary to-money #{0029B7D2DCC80CD2E3FFFFFF}")).isEqualTo(TRUE);
            assertThat(answerTo("mold to-binary $15"))
                    .isEqualTo("\"#{00000000000000000000000F}\"");
        }

        @Test
        @DisplayName("the significand carries across its three fields")
        void theSignificandCarriesAcrossItsFields() {
            assertThat(answerTo("(to-money #{000100000000000000000000}) "
                    + "= ($1 + to-money #{0000FFFFFFFFFFFFFFFFFFFF})")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("twelve bytes naming more than twenty-six digits are refused")
        void aBinaryPastTheBoundIsRefused() {
            assertThat(errorIdOf("to money! #{00FFFFFFFFFFFFFFFFFFFFFF}"))
                    .isEqualTo("overflow");
        }
    }

    @Nested
    @DisplayName("REBTYPE(Money): the binary actions")
    class Arithmetic {

        @Test
        @DisplayName("the answer stays a money whichever number met it")
        void theAnswerStaysAMoney() {
            assertThat(answerTo("$8.00 == add $4.00 $4.00")).isEqualTo(TRUE);
            assertThat(answerTo("$0.00 == subtract $4.00 $4.00")).isEqualTo(TRUE);
            assertThat(answerTo("$16.00 == multiply $4.00 $4.00")).isEqualTo(TRUE);
            assertThat(answerTo("$1.00 == divide $4.00 $4.00")).isEqualTo(TRUE);
            assertThat(answerTo("money? $4 * 2")).isEqualTo(TRUE);
            assertThat(answerTo("money? $4 * 2.5")).isEqualTo(TRUE);
            assertThat(answerTo("money? $4 * 50%")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("multiplying by a time reads the time as hours")
        void multiplyingByATimeReadsHours() {
            assertThat(answerTo("$7.5 = ($5 * 1:30:0)")).isEqualTo(TRUE);
            assertThat(answerTo("$5 = ($5 * 1:0:0)")).isEqualTo(TRUE);
            assertThat(answerTo("$10 = ($5 * 2:0:0)")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("every other operation refuses a time")
        void everyOtherOperationRefusesATime() {
            assertThat(errorIdOf("$5 / 1:30:0")).isNotEqualTo(NO_ERROR);
            assertThat(errorIdOf("$5 + 1:30:0")).isNotEqualTo(NO_ERROR);
            assertThat(errorIdOf("$5 - 1:30:0")).isNotEqualTo(NO_ERROR);
            assertThat(errorIdOf("$1 - 10:30")).isNotEqualTo(NO_ERROR);
        }

        @Test
        @DisplayName("a character and a tuple are refused, though both carry numbers")
        void anythingElseIsRefused() {
            assertThat(errorIdOf("$1 / #\"a\"")).isNotEqualTo(NO_ERROR);
            assertThat(errorIdOf("$1 * 1.2.3.4")).isNotEqualTo(NO_ERROR);
            assertThat(errorIdOf("$1 + \"a\"")).isNotEqualTo(NO_ERROR);
        }

        @Test
        @DisplayName("NEGATE and ABSOLUTE only touch the sign bit")
        void theSignBitIsAllThatMoves() {
            assertThat(answerTo("mold negate $1.50")).isEqualTo("\"-$1.50\"");
            assertThat(answerTo("mold absolute -$1.50")).isEqualTo("\"$1.50\"");
            assertThat(answerTo("mold absolute $1.50")).isEqualTo("\"$1.50\"");
        }

        @Test
        @DisplayName("EVEN? and ODD? ask about the whole number the money truncates to")
        void parityLooksAtTheWholeNumber() {
            assertThat(answerTo("even? $2")).isEqualTo(TRUE);
            assertThat(answerTo("odd? $3")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("the ninety-six bit bound")
    class TheBound {

        @Test
        @DisplayName("doubling one pound five hundred and eight times is the largest money there is")
        void theBoundIsReachable() {
            assertThat(answerTo(
                    "m: $1 for i 1 508 1 [m: m * 2] m = $83798799562141231872337704e127"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("doubling once more raises overflow")
        void pastTheBoundItRaises() {
            assertThat(answerTo(
                    "m: $1 for i 1 508 1 [m: m * 2] e: try [m * 2] e/id")).isEqualTo("overflow");
        }

        @Test
        @DisplayName("making a money from a number past the bound raises")
        void makingOnePastTheBoundRaises() {
            assertThat(errorIdOf("to money! 2 ** 509")).isNotEqualTo(NO_ERROR);
        }
    }

    @Nested
    @DisplayName("A_ROUND: the scale decides the datatype of the answer")
    class Rounding {

        @Test
        @DisplayName("plain ROUND keeps the money")
        void plainRoundKeepsTheMoney() {
            assertThat(answerTo("$1 = round $1.4999")).isEqualTo(TRUE);
            assertThat(answerTo("$2 = round $1.5")).isEqualTo(TRUE);
            assertThat(answerTo("-$2 = round -$1.5")).isEqualTo(TRUE);
            assertThat(answerTo("$123 = round $123.123")).isEqualTo(TRUE);
            assertThat(answerTo("money? round $1.5")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a money scale keeps the money")
        void aMoneyScaleKeepsTheMoney() {
            assertThat(answerTo("$1.375 = round/to $1.333 $.125")).isEqualTo(TRUE);
            assertThat(answerTo("$1.33 = round/to $1.333 $.01")).isEqualTo(TRUE);
            assertThat(answerTo("$1 = round/to $0.5 $1")).isEqualTo(TRUE);
            assertThat(answerTo("$0 = round/to $0.499 $1")).isEqualTo(TRUE);
            assertThat(answerTo("$0.9 = round/to $1 $0.9")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a decimal scale gives a decimal back")
        void aDecimalScaleGivesADecimal() {
            assertThat(answerTo("1.375 = round/to $1.333 .125")).isEqualTo(TRUE);
            assertThat(answerTo("1.33 = round/to $1.333 .01")).isEqualTo(TRUE);
            assertThat(answerTo("decimal? round/to $1.333 .01")).isEqualTo(TRUE);
            assertThat(answerTo("0.1231 = round/to to-money 0.123123 0.0001"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("12.12 = round/to $12.1231 0.01")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an integer scale gives an integer back")
        void anIntegerScaleGivesAnInteger() {
            assertThat(answerTo("1 = round/to $0.5 1")).isEqualTo(TRUE);
            assertThat(answerTo("0 = round/to $0.499 1")).isEqualTo(TRUE);
            assertThat(answerTo("integer? round/to $0.5 1")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("Cmp_Money and MAX/MIN")
    class Comparing {

        @Test
        @DisplayName("MAX and MIN compare across the number datatypes and keep the money")
        void extremesCompareAcrossTheNumbers() {
            assertThat(answerTo("$3 == max $3 1")).isEqualTo(TRUE);
            assertThat(answerTo("$3 == max $3 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("$3 == max $3 $1")).isEqualTo(TRUE);
            assertThat(answerTo("-$3 == min -$3 2")).isEqualTo(TRUE);
            assertThat(answerTo("-$3 == min -$3 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("-$3 == min -$3 $1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a money is not strictly equal to the integer of the same amount")
        void strictEqualityMindsTheDatatype() {
            assertThat(answerTo("not strict-equal? $1 1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a money keeps its scale for printing and ignores it for comparing")
        void theScaleIsForPrintingOnly() {
            assertThat(answerTo("$1.50 = $1.5")).isEqualTo(TRUE);
            assertThat(answerTo("mold $1.50")).isEqualTo("\"$1.50\"");
            assertThat(answerTo("mold $1.5")).isEqualTo("\"$1.5\"");
        }
    }
}
