package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WritingADatesPartsFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        String whole = """
                said: func [answer] [
                    either error? answer [ajoin ["!" answer/id]] [mold answer]
                ]
                """ + source;
        interpreter.defineFreshWordsIn(whole);
        return interpreter.display(interpreter.run(whole));
    }

    @Nested
    @DisplayName("by number, in the order the C's own word list has them")
    class ByNumber {

        @Test
        @DisplayName("one to fourteen write the parts one to fourteen read")
        void oneToFourteenWriteWhatOneToFourteenRead() {
            assertThat(answerTo("""
                    collect [
                        repeat i 14 [
                            keep said try [d: 1-Jan-2000/12:30:45+2:00 d/:i: 3 d]
                        ]
                    ]""")).isEqualTo("""
                            ["1-Jan-0003/12:30:45+2:00" "1-Mar-2000/12:30:45+2:00" \
                            "3-Jan-2000/12:30:45+2:00" "1-Jan-2000/0:00:03+2:00" \
                            "!bad-field-set" "1-Jan-2000/12:30:45+3:00" \
                            "1-Jan-2000/3:30:45+2:00" "1-Jan-2000/12:03:45+2:00" \
                            "1-Jan-2000/12:30:03+2:00" "!bad-path-set" \
                            "3-Jan-2000/12:30:45+2:00" "1-Jan-2000/13:30:45+3:00" \
                            "!bad-field-set" "!bad-field-set"]""");
        }

        @Test
        @DisplayName("and fifteen names no part at all")
        void fifteenNamesNoPartAtAll() {
            assertThat(answerTo("""
                    collect [
                        foreach i [0 15 99 -1] [
                            keep said try [d: 1-Jan-2000 d/:i: 3 d]
                        ]
                    ]""")).isEqualTo("""
                            ["!invalid-path" "!invalid-path" "!invalid-path" \
                            "!invalid-path"]""");
        }
    }

    @Nested
    @DisplayName("the three parts that take something other than a number")
    class TheDateShapedParts {

        @Test
        @DisplayName("UTC takes a date and leaves the instant it names with no offset")
        void utcTakesADateAndLeavesTheInstantWithNoOffset() {
            assertThat(answerTo("""
                    collect [
                        foreach given reduce [
                            5-May-2005  5-May-2005/1:00+1:00  5-May-2005/1:00
                        ] [
                            keep said try [d: 1-Jan-2000/12:30:45+2:00 d/utc: given d]
                        ]
                    ]""")).isEqualTo("""
                            ["5-May-2005" "5-May-2005/0:00" "5-May-2005/1:00"]""");
        }

        @Test
        @DisplayName("JULIAN takes a decimal and answers the Gregorian day with no offset")
        void julianTakesADecimal() {
            assertThat(answerTo("""
                    collect [
                        foreach given [2415020.5 2451545.0 2400000.5] [
                            keep said try [d: 1-Jan-2000/12:30:45+2:00 d/julian: given d]
                        ]
                    ]""")).isEqualTo("""
                            ["1-Jan-1900/0:00" "1-Jan-2000/12:00" "17-Nov-1858/0:00"]""");
        }

        @Test
        @DisplayName("DATE takes a date and takes its offset with it")
        void dateTakesADateAndItsOffset() {
            assertThat(answerTo("""
                    collect [
                        foreach given reduce [5-May-2005  5-May-2005/1:00+1:00] [
                            keep said try [d: 1-Jan-2000/12:30:45+2:00 d/date: given d]
                        ]
                    ]""")).isEqualTo("""
                            ["5-May-2005/12:30:45" "5-May-2005/12:30:45+1:00"]""");
        }

        @Test
        @DisplayName("and each of the three refuses what the other two would take")
        void eachRefusesWhatTheOthersWouldTake() {
            assertThat(answerTo("""
                    collect [
                        foreach given reduce [1 none "x" 1-Jan-2000] [
                            keep said try [d: 1-Jan-2000 d/julian: given d]
                        ]
                        foreach given reduce [1 none "x"] [
                            keep said try [d: 1-Jan-2000 d/utc: given d]
                        ]
                        foreach given reduce [1 none] [
                            keep said try [d: 1-Jan-2000 d/date: given d]
                        ]
                    ]""")).isEqualTo("""
                            ["!bad-field-set" "!bad-field-set" "!bad-field-set" \
                            "!bad-field-set" "!bad-field-set" "!bad-field-set" \
                            "!bad-field-set" "!bad-field-set" "!bad-field-set"]""");
        }

        @Test
        @DisplayName("a Julian count the conversion cannot reach says type-limit")
        void aJulianCountTheConversionCannotReach() {
            assertThat(answerTo("""
                    collect [
                        foreach given [0.0 1.5] [
                            keep said try [d: 1-Jan-2000 d/julian: given d]
                        ]
                    ]""")).isEqualTo("""
                            ["!type-limit" "!type-limit"]""");
        }
    }

    @Nested
    @DisplayName("the zone, which takes several shapes")
    class TheZone {

        @Test
        @DisplayName("a number of hours, a time, or none for no offset at all")
        void aNumberATimeOrNone() {
            assertThat(answerTo("""
                    collect [
                        foreach given reduce [none 0 -5 3:00] [
                            keep said try [d: 1-Jan-2000/12:30:45+2:00 d/zone: given d]
                        ]
                    ]""")).isEqualTo("""
                            ["1-Jan-2000/12:30:45" "1-Jan-2000/12:30:45" \
                            "1-Jan-2000/12:30:45-5:00" "1-Jan-2000/12:30:45+3:00"]""");
        }

        @Test
        @DisplayName("and past sixteen hours either way it is out of range")
        void pastSixteenHoursEitherWay() {
            assertThat(answerTo("""
                    collect [
                        foreach given [16 -16] [
                            keep said try [d: 1-Jan-2000/12:30:45+2:00 d/zone: given d]
                        ]
                    ]""")).isEqualTo("""
                            ["!out-of-range" "!out-of-range"]""");
        }
    }

    @Test
    @DisplayName("a date on the right of a subtraction needs a date on the left")
    void aDateOnTheRightNeedsADateOnTheLeft() {
        assertThat(answerTo("""
                collect [
                    foreach given reduce [0 1.5 1:00 $1 1-Jan-2000] [
                        keep said try [given - 1-Jan-2000]
                    ]
                ]""")).isEqualTo("""
                        ["!not-related" "!not-related" "!not-related" \
                        "!not-related" "0"]""");
    }

    @Test
    @DisplayName("and a date on the left still takes a count of days")
    void aDateOnTheLeftStillTakesACountOfDays() {
        assertThat(answerTo("""
                collect [foreach given [0 1 -1] [keep said try [1-Jan-2000 - given]]]"""))
                .isEqualTo("""
                        ["1-Jan-2000" "31-Dec-1999" "2-Jan-2000"]""");
    }
}
