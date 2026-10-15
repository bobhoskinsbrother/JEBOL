package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MakingATimeFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String EACH_ONES_ANSWER_OR_ITS_ERROR_ID = """
            collect [
                foreach given SUBJECTS [
                    keep either error? e: try [to time! given] [e/id] [e]
                ]
            ]""";

    private static String whatEachOneMakes(String subjects) {
        return answerTo(EACH_ONES_ANSWER_OR_ITS_ERROR_ID.replace("SUBJECTS", subjects));
    }

    @Nested
    @DisplayName("from a string the reader could read")
    class FromAString {

        @Test
        @DisplayName("a leading sign is read, and the plus is accepted as well as the minus")
        void aLeadingSignIsRead() {
            assertThat(whatEachOneMakes("""
                    ["+02:00" "-02:00" "02:00" "2:00" "-0:0:1" "+2:00:03.25"]"""))
                    .isEqualTo("[2:00 -2:00 2:00 2:00 -0:00:01 2:00:03.25]");
        }

        @Test
        @DisplayName("two parts are hours and minutes, and with a fraction minutes and seconds")
        void twoPartsAreHoursAndMinutesUnlessThereIsAFraction() {
            assertThat(whatEachOneMakes("""
                    ["0:0" "12:34" "12:34.5" "-12:34.5" "+12:34.5"]"""))
                    .isEqualTo("[0:00 12:34 0:12:34.5 -0:12:34.5 0:12:34.5]");
        }

        @Test
        @DisplayName("three parts are hours, minutes and seconds, and a comma marks a fraction")
        void threePartsAndACommaForAFraction() {
            assertThat(whatEachOneMakes("""
                    ["1:2:3" "0:0:0" "1:2:3.5" "1:2:3,5" "1:2.5:3"]"""))
                    .isEqualTo("[1:02:03 0:00 1:02:03.5 1:02:03.5 0:01:02.5]");
        }

        @Test
        @DisplayName("an AM or PM suffix moves the hour, and noon and midnight are the corners")
        void anAfternoonSuffixMovesTheHour() {
            assertThat(whatEachOneMakes("""
                    ["2:00PM" "2:00pm" "2:00AM" "11:59PM" "12:00AM" "12:00PM" "13:00PM"]"""))
                    .isEqualTo("[14:00 14:00 2:00 23:59 0:00 12:00 bad-make-arg]");
        }

        @Test
        @DisplayName("whitespace at either end is stepped over, and so are leading noughts")
        void whitespaceAtEitherEndIsSteppedOver() {
            assertThat(whatEachOneMakes("""
                    [" 2:00" "2:00 " "^-2:00" "2:00^-" "2:00^/" "00000002:00"]"""))
                    .isEqualTo("[2:00 2:00 2:00 2:00 2:00 2:00]");
        }

        @Test
        @DisplayName("and anything the scanner has not reached by the end is ignored")
        void anythingAfterTheTimeIsIgnored() {
            assertThat(whatEachOneMakes("""
                    ["2:00xyz" "2:00:00junk"]""")).isEqualTo("[2:00 2:00]");
        }

        @Test
        @DisplayName("MAKE and TO read a string the same way")
        void makeAndToReadAStringTheSameWay() {
            assertThat(answerTo("""
                    reduce [make time! "+02:00"  to time! "+02:00"]"""))
                    .isEqualTo("[2:00 2:00]");
        }
    }

    @Nested
    @DisplayName("from a string the reader could not")
    class FromABadString {

        @Test
        @DisplayName("two signs in a row, and every other shape that is not a time")
        void everyShapeThatIsNotATime() {
            assertThat(whatEachOneMakes("""
                    ["--1:23" "+-1:23" "+" "-" "abc" ":" "1:" "1:2:" "1.5:00"]"""))
                    .isEqualTo("""
                            [bad-make-arg bad-make-arg bad-make-arg bad-make-arg \
                            bad-make-arg bad-make-arg bad-make-arg bad-make-arg \
                            bad-make-arg]""");
        }

        @Test
        @DisplayName("the refusal names the datatype asked for and the text that would not do")
        void theRefusalNamesTheDatatypeAndTheText() {
            assertThat(answerTo("""
                    e: try [to time! "abc"]
                    reduce [e/id e/arg1 e/arg2]"""))
                    .isEqualTo("""
                            [bad-make-arg #(time!) "abc"]""");
        }

        @Test
        @DisplayName("nothing but whitespace is too short, and past thirty characters too long")
        void nothingButWhitespaceIsTooShortAndPastThirtyTooLong() {
            assertThat(whatEachOneMakes("""
                    ["" "   " "^-"]""")).isEqualTo("[too-short too-short too-short]");
            assertThat(answerTo("""
                    collect [
                        foreach noughts [29 30 31] [
                            given: ajoin [append/dup copy "" #"0" noughts - 3 "1:0"]
                            keep either error? e: try [to time! given] [e/id] [e]
                        ]
                    ]""")).isEqualTo("[1:00 1:00 too-long]");
        }

        @Test
        @DisplayName("a second run of characters after the time is invalid, and so is any non-ASCII")
        void asecondRunAfterTheTimeIsInvalidAndSoIsNonAscii() {
            assertThat(whatEachOneMakes("""
                    ["2:00 am" "2:00 00" "^(E9)" "2:0^(E9)"]"""))
                    .isEqualTo("""
                            [invalid-chars invalid-chars invalid-chars invalid-chars]""");
        }
    }

    @Nested
    @DisplayName("the hour is bounded and nothing after it is")
    class TheWidestTime {

        @Test
        @DisplayName("the widest whole hour is read and one more is refused")
        void theWidestWholeHourIsReadAndOneMoreIsRefused() {
            assertThat(whatEachOneMakes("""
                    ["2562047:00" "2562048:00"]"""))
                    .isEqualTo("[2562047:00 bad-make-arg]");
        }

        @Test
        @DisplayName("and minutes on top of the widest hour wrap round, as they do in the C")
        void minutesOnTopOfTheWidestHourWrapRound() {
            assertThat(answerTo("to time! {2562047:59:59}"))
                    .isEqualTo("-2562047:34:34.709551616");
        }
    }

    @Nested
    @DisplayName("from a block of up to three numbers")
    class FromABlock {

        @Test
        @DisplayName("hours, then minutes, then seconds, filled from the left")
        void hoursThenMinutesThenSeconds() {
            assertThat(whatEachOneMakes("""
                    [[1] [1 2] [1 2 3] [24 0 0] [0 0 0]]"""))
                    .isEqualTo("[1:00 1:02 1:02:03 24:00 0:00]");
        }

        @Test
        @DisplayName("the last part may be fractional")
        void theLastPartMayBeFractional() {
            assertThat(whatEachOneMakes("""
                    [[1 2 3.5] [0 0 0.5]]""")).isEqualTo("[1:02:03.5 0:00:00.5]");
        }

        @Test
        @DisplayName("a minus on the hours negates the whole time, and lower down is refused")
        void aMinusOnTheHoursNegatesTheWholeTime() {
            assertThat(whatEachOneMakes("""
                    [[-1 2 3] [1 -2 3] [1 2 -3]]"""))
                    .isEqualTo("[-1:02:03 bad-make-arg bad-make-arg]");
        }

        @Test
        @DisplayName("the widest hour holds here too")
        void theWidestHourHoldsHereToo() {
            assertThat(whatEachOneMakes("""
                    [[2562047 0 0] [2562048 0 0]]"""))
                    .isEqualTo("[2562047:00 bad-make-arg]");
        }

        @Test
        @DisplayName("an empty block, a fourth part, and a part that is not a number are refused")
        void everyOtherBlockIsRefused() {
            assertThat(whatEachOneMakes("""
                    [[] [1 2 3 4] ["a"] [1 "a"] [1x1] [1.5]]"""))
                    .isEqualTo("""
                            [bad-make-arg bad-make-arg bad-make-arg bad-make-arg \
                            bad-make-arg bad-make-arg]""");
        }
    }

    @Nested
    @DisplayName("from a number, a time, or something else entirely")
    class FromEverythingElse {

        @Test
        @DisplayName("a number is its seconds, and a time is itself")
        void aNumberIsItsSecondsAndATimeIsItself() {
            assertThat(whatEachOneMakes("[5 5.5 -3 0 2:00]"))
                    .isEqualTo("[0:00:05 0:00:05.5 -0:00:03 0:00 2:00]");
        }

        @Test
        @DisplayName("and the widest number of seconds is the edge")
        void theWidestNumberOfSecondsIsTheEdge() {
            assertThat(whatEachOneMakes("""
                    [9223372036 9223372037 -9223372036 -9223372037 9223372037.0]"""))
                    .isEqualTo("""
                            [2562047:47:16 out-of-range -2562047:47:16 out-of-range \
                            out-of-range]""");
        }

        @Test
        @DisplayName("anything else makes no time at all")
        void anythingElseMakesNoTimeAtAll() {
            assertThat(whatEachOneMakes("""
                    [#{3200} 'word #"a" %f #(true) #(none)]"""))
                    .isEqualTo("""
                            [bad-make-arg bad-make-arg bad-make-arg bad-make-arg \
                            bad-make-arg bad-make-arg]""");
        }

        @Test
        @DisplayName("and says so about the datatype asked for and the value given")
        void andSaysSoAboutTheDatatypeAndTheValue() {
            assertThat(answerTo("""
                    e: try [to time! #{3200}]
                    reduce [e/id e/arg1 e/arg2]"""))
                    .isEqualTo("[bad-make-arg #(time!) #{3200}]");
        }
    }
}
