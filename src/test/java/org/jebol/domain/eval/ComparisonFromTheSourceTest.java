package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comparison, read out of {@code Compare_Values} in {@code src/core/n-math.c}
 * and the {@code CT_} function of each datatype it dispatches to.
 *
 * <p>Written from the C and not from the Java beside it. Each group names the
 * function it was taken from, so a disagreement is settled by reading that
 * function rather than by arguing about what a comparison ought to answer.
 *
 * <p>The one idea underneath all of it: there is a single comparison, and
 * every comparison native is a call to it with a number saying how strict to
 * be. Nought is EQUAL?, one is EQUIV?, two is {@code ==}, three is SAME?, and
 * the two negative ones are the ordering questions. That number decides three
 * separate things at once -- whether two different datatypes may be brought
 * together at all, how far two decimals may drift apart, and whether a
 * mismatch answers false or refuses to answer -- which is why writing an
 * answer per native rather than per strictness makes the natives disagree.
 */
class ComparisonFromTheSourceTest {

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
    private static final String INVALID_COMPARE = "invalid-compare";
    private static final String NO_ERROR = "no-error";

    @Nested
    @DisplayName("the natives: ten spellings, six questions")
    class WhichNativeAsksWhat {

        @Test
        @DisplayName("each native passes its own strictness, and half of them negate")
        void theTenNativesAskSixQuestions() {
            assertThat(answerTo("equal? 1 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("1 = 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("not-equal? 1 1.0")).isEqualTo(FALSE);
            assertThat(answerTo("1 <> 1.0")).isEqualTo(FALSE);
            assertThat(answerTo("1 != 1.0")).isEqualTo(FALSE);

            assertThat(answerTo("equiv? 1 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("not-equiv? 1 1.0")).isEqualTo(FALSE);

            assertThat(answerTo("strict-equal? 1 1.0")).isEqualTo(FALSE);
            assertThat(answerTo("1 == 1.0")).isEqualTo(FALSE);
            assertThat(answerTo("strict-not-equal? 1 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("1 !== 1.0")).isEqualTo(TRUE);

            assertThat(answerTo("same? 1 1.0")).isEqualTo(FALSE);
            assertThat(answerTo("1 =? 1.0")).isEqualTo(FALSE);

            assertThat(answerTo("greater? 2 1")).isEqualTo(TRUE);
            assertThat(answerTo("greater-or-equal? 1 1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("LESSER? is the greater-or-equal question negated, not a question of its own")
        void lesserIsGreaterOrEqualNegated() {
            assertThat(answerTo("lesser? 1 2")).isEqualTo(TRUE);
            assertThat(answerTo("lesser? 1 1")).isEqualTo(FALSE);
            assertThat(answerTo("lesser-or-equal? 1 1")).isEqualTo(TRUE);
            assertThat(answerTo("lesser-or-equal? 2 1")).isEqualTo(FALSE);
            assertThat(answerTo("1 < 2")).isEqualTo(TRUE);
            assertThat(answerTo("1 <= 1")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("Compare_Values: the early exit at strictness above one")
    class TheStrictExit {

        @Test
        @DisplayName("== and SAME? answer false for two datatypes rather than coercing")
        void strictEqualityRefusesTwoDatatypes() {
            assertThat(answerTo("97 == 97.0")).isEqualTo(FALSE);
            assertThat(answerTo("97 == 9700%")).isEqualTo(FALSE);
            assertThat(answerTo("97 == #\"a\"")).isEqualTo(FALSE);
            assertThat(answerTo("97 == 0:01:37")).isEqualTo(FALSE);
            assertThat(answerTo("$1 == 1")).isEqualTo(FALSE);
            assertThat(answerTo("97.0 == 9700%")).isEqualTo(FALSE);
            assertThat(answerTo("same? 1 1.0")).isEqualTo(FALSE);
            assertThat(answerTo("same? $1 1")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("EQUIV? is one below the exit, so it coerces as EQUAL? does")
        void equivCoercesLikeEqual() {
            assertThat(answerTo("equiv? 1 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("equiv? 97 9700%")).isEqualTo(TRUE);
            assertThat(answerTo("equiv? $1 1")).isEqualTo(TRUE);
            assertThat(answerTo("equiv? 0:0:1 1")).isEqualTo(TRUE);
            assertThat(answerTo("equiv? #\"a\" 97")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("Compare_Values: the coercion table, one case label at a time")
    class TheCoercionTable {

        @Test
        @DisplayName("an integer meets a decimal, a percent and a money")
        void integerMeetsEveryNumber() {
            assertThat(answerTo("97 = 97.0")).isEqualTo(TRUE);
            assertThat(answerTo("97 = 9700%")).isEqualTo(TRUE);
            assertThat(answerTo("1 = $1")).isEqualTo(TRUE);
            assertThat(answerTo("97 < 97.1")).isEqualTo(TRUE);
            assertThat(answerTo("97 < 9701%")).isEqualTo(TRUE);
            assertThat(answerTo("97 > 96.0")).isEqualTo(TRUE);
            assertThat(answerTo("1 < $2")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an integer meets a character, comparing against its code point")
        void integerMeetsACharacter() {
            assertThat(answerTo("97 = #\"a\"")).isEqualTo(TRUE);
            assertThat(answerTo("97 < #\"b\"")).isEqualTo(TRUE);
            assertThat(answerTo("98 > #\"a\"")).isEqualTo(TRUE);
            assertThat(answerTo("48 = #\"0\"")).isEqualTo(TRUE);
            assertThat(answerTo("equal? 10000 #\"^(2710)\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an integer meets a time, both becoming seconds")
        void integerMeetsATime() {
            assertThat(answerTo("97 = 0:01:37")).isEqualTo(TRUE);
            assertThat(answerTo("97 < 0:01:38")).isEqualTo(TRUE);
            assertThat(answerTo("98 > 0:01:37")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("an integer meets nothing else, so the case label ends there")
        void integerRefusesEverythingElse() {
            assertThat(answerTo("90 = \"a\"")).isEqualTo(FALSE);
            assertThat(answerTo("90 = 1x1")).isEqualTo(FALSE);
            assertThat(answerTo("90 = 'a")).isEqualTo(FALSE);
            assertThat(answerTo("1 = 1.1.1")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a decimal meets an integer, a money and a percent")
        void decimalMeetsEveryNumber() {
            assertThat(answerTo("97.0 = 97")).isEqualTo(TRUE);
            assertThat(answerTo("97.0 = 9700%")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 = $1")).isEqualTo(TRUE);
            assertThat(answerTo("97.0 < 98")).isEqualTo(TRUE);
            assertThat(answerTo("97.0 < 9701%")).isEqualTo(TRUE);
            assertThat(answerTo("97.0 > 9600%")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a decimal meets a time, the time becoming seconds")
        void decimalMeetsATime() {
            assertThat(answerTo("97.0 = 0:01:37")).isEqualTo(TRUE);
            assertThat(answerTo("97.0 < 0:01:38")).isEqualTo(TRUE);
            assertThat(answerTo("98.0 > 0:01:37")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a percent shares the decimal's case label, so it meets the same set")
        void percentTakesTheDecimalCaseLabel() {
            assertThat(answerTo("9700% = 97")).isEqualTo(TRUE);
            assertThat(answerTo("9700% = 97.0")).isEqualTo(TRUE);
            assertThat(answerTo("100% = $1")).isEqualTo(TRUE);
            assertThat(answerTo("100% = 0:0:1")).isEqualTo(TRUE);
            assertThat(answerTo("100% < 200%")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a decimal does not meet a character, because the label omits it")
        void decimalRefusesACharacter() {
            assertThat(answerTo("97.0 = #\"a\"")).isEqualTo(FALSE);
            assertThat(errorIdOf("97.0 < #\"a\"")).isEqualTo(INVALID_COMPARE);
        }

        @Test
        @DisplayName("a money meets an integer, a decimal and a percent")
        void moneyMeetsTheOtherNumbers() {
            assertThat(answerTo("$1 = 1")).isEqualTo(TRUE);
            assertThat(answerTo("$1 = 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("$1 = 100%")).isEqualTo(TRUE);
            assertThat(answerTo("$1 < 2")).isEqualTo(TRUE);
            assertThat(answerTo("$2 > 1.5")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a money does not meet a time, in either direction")
        void moneyRefusesATime() {
            assertThat(answerTo("equal? 0:0:1 $1")).isEqualTo(FALSE);
            assertThat(answerTo("equal? $1 0:0:1")).isEqualTo(FALSE);
            assertThat(errorIdOf("$1 < 0:0:2")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("0:0:1 < $2")).isEqualTo(INVALID_COMPARE);
        }

        @Test
        @DisplayName("any word meets any other word")
        void wordsMeetEachOther() {
            assertThat(answerTo("equal? [a] [a:]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [a] [:a]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [a] ['a]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [a] [/a]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? first [a] first [a:]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("any string meets any other string")
        void stringsMeetEachOther() {
            assertThat(answerTo("equal? \"a\" %a")).isEqualTo(TRUE);
            assertThat(answerTo("equal? \"a\" <a>")).isEqualTo(TRUE);
            assertThat(answerTo("equal? \"a\" to email! \"a\"")).isEqualTo(TRUE);
            assertThat(answerTo("\"a\" < %b")).isEqualTo(TRUE);
            assertThat(answerTo("equal? \"a\" #\"a\"")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a character meets an integer and no other number")
        void characterMeetsAnIntegerOnly() {
            assertThat(answerTo("#\"a\" = 97")).isEqualTo(TRUE);
            assertThat(answerTo("#\"a\" = 97.0")).isEqualTo(FALSE);
            assertThat(answerTo("#\"a\" = $97")).isEqualTo(FALSE);
            assertThat(answerTo("#\"a\" = 97%")).isEqualTo(FALSE);
            assertThat(answerTo("#\"a\" <> 97.0")).isEqualTo(TRUE);
            assertThat(answerTo("#\"a\" <> $97")).isEqualTo(TRUE);
            assertThat(answerTo("#\"a\" <> 97%")).isEqualTo(TRUE);
            assertThat(answerTo("#\"a\" <> \"a\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a time meets the three non-money numbers")
        void timeMeetsTheNonMoneyNumbers() {
            assertThat(answerTo("0:0:1 = 1")).isEqualTo(TRUE);
            assertThat(answerTo("0:0:1 = 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("0:0:1 = 100%")).isEqualTo(TRUE);
            assertThat(answerTo("0:0:1 < 2")).isEqualTo(TRUE);
            assertThat(answerTo("0:0:2 > 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("0:0:1 < 200%")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a datatype with no case label at all falls straight through")
        void uncoercibleTypesAnswerFalse() {
            assertThat(answerTo("1x1 = 1")).isEqualTo(FALSE);
            assertThat(answerTo("equiv? 1x1 1")).isEqualTo(FALSE);
            assertThat(answerTo("[1] = 1")).isEqualTo(FALSE);
            assertThat(answerTo("\"a\" = #\"a\"")).isEqualTo(FALSE);
            assertThat(errorIdOf("1x1 = 1")).isEqualTo(NO_ERROR);
            assertThat(errorIdOf("\"a\" <> #\"a\"")).isEqualTo(NO_ERROR);
        }
    }

    @Nested
    @DisplayName("Compare_Values: Trap2(RE_INVALID_COMPARE)")
    class TheRefusal {

        @Test
        @DisplayName("ordering two datatypes that do not coerce raises invalid-compare")
        void orderingUncoercibleTypesRaises() {
            assertThat(errorIdOf("#\"a\" < 98.0")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("#\"a\" < $98")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("#\"a\" < 98%")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("#\"a\" < \"a\"")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("#\"a\" < 1x1")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("90 < \"a\"")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("90 < 1x1")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("90.0 < \"a\"")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("90.0 < 1x1")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("0:0:0 < \"a\"")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("0:0:0 < 1x1")).isEqualTo(INVALID_COMPARE);
        }

        @Test
        @DisplayName("all four ordering natives raise, because all four ask the same two questions")
        void everyOrderingSpellingRaises() {
            assertThat(errorIdOf("90 < 1x1")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("90 <= 1x1")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("90 > 1x1")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("90 >= 1x1")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("lesser? 90 1x1")).isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("greater? 90 1x1")).isEqualTo(INVALID_COMPARE);
        }

        @Test
        @DisplayName("the same pairings under the four sameness natives answer instead")
        void everyPairingUnderEqualAnswers() {
            assertThat(errorIdOf("90 = 1x1")).isEqualTo(NO_ERROR);
            assertThat(errorIdOf("equiv? 90 1x1")).isEqualTo(NO_ERROR);
            assertThat(errorIdOf("90 == 1x1")).isEqualTo(NO_ERROR);
            assertThat(errorIdOf("same? 90 1x1")).isEqualTo(NO_ERROR);
        }

        @Test
        @DisplayName("ordering two objects raises, because CT_Object refuses below mode nought")
        void orderingTwoObjectsRaises() {
            assertThat(errorIdOf("(construct [c: 1]) < construct [c: 2]"))
                    .isEqualTo(INVALID_COMPARE);
            assertThat(errorIdOf("(construct [c: 1]) > construct [c: 2]"))
                    .isEqualTo(INVALID_COMPARE);
        }
    }

    @Nested
    @DisplayName("CT_Integer, t-integer.c")
    class Integers {

        @Test
        @DisplayName("equality above mode nought, then >= and > below it")
        void integersCompareAsWholeNumbers() {
            assertThat(answerTo("1 = 1")).isEqualTo(TRUE);
            assertThat(answerTo("1 = 2")).isEqualTo(FALSE);
            assertThat(answerTo("1 >= 1")).isEqualTo(TRUE);
            assertThat(answerTo("1 >= 2")).isEqualTo(FALSE);
            assertThat(answerTo("2 >= 1")).isEqualTo(TRUE);
            assertThat(answerTo("1 > 1")).isEqualTo(FALSE);
            assertThat(answerTo("2 > 1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the ends of the range compare as signed whole numbers")
        void integersCompareSigned() {
            assertThat(answerTo("equal? 0#FFFFFFFFFFFFFFFF -1")).isEqualTo(TRUE);
            assertThat(answerTo("greater? 0#FFFFFFFFFFFFFFFF -1")).isEqualTo(FALSE);
            assertThat(answerTo("greater? -1 0#FFFFFFFFFFFFFFFE")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("CT_Decimal and almost_equal, t-decimal.c")
    class Decimals {

        @Test
        @DisplayName("EQUAL? allows twenty-one steps of the representation and no more")
        void theAllowanceIsTwentyOneSteps() {
            assertThat(answerTo("1.0 = 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 = 1.0000000000000002")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 = 1.0000000000000044")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 = 1.0000000000000047")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 = 1.0000000000000049")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("the assertion Rebol's own comment cites beside the twenty-one")
        void theCitedAssertionHolds() {
            assertThat(answerTo("100% %% 3% = 1%")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("EQUIV? allows no steps at all")
        void equivAllowsNoSteps() {
            assertThat(answerTo("equiv? 1.0 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("equiv? 1.0 1.0000000000000002")).isEqualTo(FALSE);
            assertThat(answerTo("equal? 1.0 1.0000000000000002")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("== and SAME? compare the bits")
        void strictEqualityComparesTheBits() {
            assertThat(answerTo("0.5 == 0.5")).isEqualTo(TRUE);
            assertThat(answerTo("0.5 == 0.5000000000000001")).isEqualTo(FALSE);
            assertThat(answerTo("same? 0.3 (0.1 + 0.1 + 0.1)")).isEqualTo(FALSE);
            assertThat(answerTo("equal? 0.3 (0.1 + 0.1 + 0.1)")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the four sameness strictnesses give four different answers about NaN")
        void notANumberUnderEachStrictness() {
            assertThat(answerTo("1.#NaN = 1.#NaN")).isEqualTo(TRUE);
            assertThat(answerTo("equiv? 1.#NaN 1.#NaN")).isEqualTo(FALSE);
            assertThat(answerTo("1.#NaN == 1.#NaN")).isEqualTo(FALSE);
            assertThat(answerTo("same? 1.#NaN 1.#NaN")).isEqualTo(TRUE);
            assertThat(answerTo("1.#NaN !== 1.#NaN")).isEqualTo(TRUE);
            assertThat(answerTo("not-equiv? 1.#NaN 1.#NaN")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("ordering below mode nought is plain floating point >= and >")
        void decimalsCompareAsNumbers() {
            assertThat(answerTo("1.0 >= 1.0")).isEqualTo(TRUE);
            assertThat(answerTo("1.0 > 1.0")).isEqualTo(FALSE);
            assertThat(answerTo("1.5 > 1.0")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("CT_Char, t-char.c")
    class Characters {

        @Test
        @DisplayName("case folds below mode two and counts at or above it")
        void charactersFoldCaseForSamenessOnly() {
            assertThat(answerTo("#\"a\" = #\"A\"")).isEqualTo(TRUE);
            assertThat(answerTo("equiv? #\"a\" #\"A\"")).isEqualTo(TRUE);
            assertThat(answerTo("#\"a\" == #\"A\"")).isEqualTo(FALSE);
            assertThat(answerTo("same? #\"a\" #\"A\"")).isEqualTo(FALSE);
            assertThat(answerTo("#\"a\" == #\"a\"")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("ordering reads the code point and folds nothing")
        void charactersOrderByCodePoint() {
            assertThat(answerTo("#\"a\" < #\"b\"")).isEqualTo(TRUE);
            assertThat(answerTo("#\"b\" > #\"a\"")).isEqualTo(TRUE);
            assertThat(answerTo("#\"a\" < #\"B\"")).isEqualTo(FALSE);
        }

        @Test
        @DisplayName("a character against an integer takes the character's path, folding both")
        void aCharacterAgainstAnIntegerFoldsBoth() {
            assertThat(answerTo("#\"a\" = 97")).isEqualTo(TRUE);
            assertThat(answerTo("#\"a\" > 96")).isEqualTo(TRUE);
            assertThat(answerTo("#\"a\" < 98")).isEqualTo(TRUE);
            assertThat(answerTo("97 = #\"a\"")).isEqualTo(TRUE);
            assertThat(answerTo("65 = #\"A\"")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("CT_Object and Equal_Object, t-object.c")
    class Objects {

        @Test
        @DisplayName("two objects holding the same number are equal across its datatypes")
        void objectFieldsCompareAsNumbers() {
            assertThat(answerTo("equal? construct [c: 1] construct [c: 1]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? construct [c: 1] construct [c: 1.0]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? construct [c: 1] construct [c: $1]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? construct [c: 1] construct [c: 100%]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? construct [c: 1.0] construct [c: 1]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? construct [c: $1] construct [c: 1]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? construct [c: 100%] construct [c: 1]")).isEqualTo(TRUE);

            assertThat(answerTo("strict-equal? construct [c: 1] construct [c: 1]"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("strict-equal? construct [c: 1] construct [c: 1.0]"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("strict-equal? construct [c: 1] construct [c: $1]"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("strict-equal? construct [c: 1] construct [c: 100%]"))
                    .isEqualTo(FALSE);
        }

        @Test
        @DisplayName("the field names have to agree, and so does how many there are")
        void objectsNeedTheSameFields() {
            assertThat(answerTo("equal? construct [c: 1] construct [d: 1]")).isEqualTo(FALSE);
            assertThat(answerTo("equal? construct [c: 1] construct [c: 1 d: 2]"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("equal? construct [c: 1 d: 2] construct [c: 1]"))
                    .isEqualTo(FALSE);
            assertThat(answerTo("equal? construct [] construct []")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("SAME? asks whether the two are one object, not whether they match")
        void sameAsksAboutOneObject() {
            assertThat(answerTo("same? construct [c: 1] construct [c: 1]")).isEqualTo(FALSE);
            assertThat(answerTo("o: construct [c: 1] same? o o")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("Cmp_Value, f-series.c: the same coercion inside a block")
    class InsideABlock {

        @Test
        @DisplayName("a number in a block coerces across its datatypes")
        void numbersInABlockCoerce() {
            assertThat(answerTo("equal? [1] [1.0]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [1] [100%]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [1] [$1]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [$1] [100%]")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [100%] [1.0]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a string, a character and a time do not coerce inside a block")
        void onlyNumbersAndWordsCoerceInsideABlock() {
            assertThat(answerTo("equal? \"a\" %a")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [\"a\"] [%a]")).isEqualTo(FALSE);

            assertThat(answerTo("equal? 0:0:1 1")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [0:0:1] [1]")).isEqualTo(FALSE);

            assertThat(answerTo("equal? #\"a\" 97")).isEqualTo(TRUE);
            assertThat(answerTo("equal? [#\"a\"] [97]")).isEqualTo(FALSE);

            assertThat(answerTo("equal? [a] [a:]")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("the series functions ask Cmp_Value, so they mind the string datatype")
        void theSeriesFunctionsGetTheStricterAnswer() {
            assertThat(answerTo("2 == first select [%a [1] \"a\" [2]] \"a\"")).isEqualTo(TRUE);
            assertThat(answerTo("2 == switch \"a\" [%a [1] \"a\" [2]]")).isEqualTo(TRUE);
            assertThat(answerTo("2 == first first find/tail [%a [1] \"a\" [2]] \"a\""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("none? find [\"a\"] %a")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and stops coercing when asked to mind the case")
        void strictComparisonInABlockMindsTheDatatype() {
            assertThat(answerTo("strict-equal? [1] [1.0]")).isEqualTo(FALSE);
            assertThat(answerTo("strict-equal? [1] [$1]")).isEqualTo(FALSE);
            assertThat(answerTo("strict-equal? [1] [1]")).isEqualTo(TRUE);
            assertThat(answerTo("strict-equal? [a] [a:]")).isEqualTo(FALSE);
        }
    }
}
