package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading a date, at every boundary {@code Scan_Date} draws.
 *
 * <p>It draws them by counting digits rather than by judging what a number
 * could plausibly mean, and that is the part a reasonable implementation gets
 * wrong. {@code if (size >= 4) year = num; else if (size) day = num;} decides
 * whether the first part is a year or a day, so {@code 2000-01-01} and
 * {@code 1-1-2000} are the same date and {@code 100-Jan-2000} is not a date
 * at all. The last part is counted the same way: three digits or more is the
 * year as written, two or fewer is a shorthand resolved against the year the
 * program is running in.
 *
 * <p>Reading day-first always meant an ISO date reached {@code DateValue.of}
 * as a day of 2000 and threw {@code IllegalArgumentException} straight out of
 * the interpreter. That is worse than a wrong answer: it is not a REBOL error,
 * so nothing could catch it, and one such literal in make-test.r3 took an
 * entire suite run down before a single assertion had run.
 *
 * <p>Every expectation here was run on a real Rebol first, built from the
 * vendored source by {@code scripts/build-r3.sh}.
 */
class DateLiteralFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String reading(String literal) {
        String shown = answerTo(
                "either error? e: try [load {" + literal + "}] [e/id] [mold e]");
        return shown.length() >= 2 && shown.charAt(0) == '"'
                && shown.charAt(shown.length() - 1) == '"'
                ? shown.substring(1, shown.length() - 1)
                : shown;
    }

    @Nested
    @DisplayName("how many digits the first part has decides what it is")
    class TheFirstPart {

        @ParameterizedTest(name = "{0} is a day")
        @ValueSource(strings = {"1-Jan-2000", "01-Jan-2000"})
        @DisplayName("one or two digits is a day")
        void oneOrTwoDigitsIsADay(String literal) {
            assertThat(reading(literal)).isEqualTo("1-Jan-2000");
        }

        @Test
        @DisplayName("four digits is a year, so the date reads the other way round")
        void fourDigitsIsAYear() {
            assertThat(reading("2000-Jan-01")).isEqualTo("1-Jan-2000");
        }

        @Test
        @DisplayName("three digits is neither, and is refused rather than thrown")
        void threeDigitsIsNeither() {
            assertThat(reading("100-Jan-2000"))
                    .as("a day of 100, which used to leave the interpreter as a "
                            + "Java IllegalArgumentException")
                    .isEqualTo("invalid");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"2000-01-01", "2000/01/01", "1/1/2000", "1-1-2000"})
        @DisplayName("either order, either separator, same date")
        void eitherOrderEitherSeparator(String literal) {
            assertThat(reading(literal)).isEqualTo("1-Jan-2000");
        }
    }

    @Nested
    @DisplayName("how many digits the last part has decides which year it means")
    class TheLastPart {

        @ParameterizedTest(name = "{0} means {1}")
        @CsvSource({
            "1-Jan-3,     1-Jan-2003",
            "1-Jan-03,    1-Jan-2003",
            "1-Jan-003,   1-Jan-0003",
            "1-Jan-0003,  1-Jan-0003",
        })
        @DisplayName("two digits or fewer is shorthand, three or more is the year itself")
        void theDigitCountDecides(String literal, String expected) {
            assertThat(reading(literal)).isEqualTo(expected);
        }

        @Test
        @DisplayName("and the shorthand stays inside fifty years of the one we are in")
        void theshorthandStaysInsideFiftyYears() {
            int thisYear = java.time.Year.now().getValue();
            int lastForward = 50 + thisYear - thisYear / 100 * 100;
            assertThat(reading("1-Jan-%02d".formatted(lastForward)))
                    .as("the last shorthand year that still counts forward")
                    .isEqualTo("1-Jan-%d".formatted(thisYear / 100 * 100 + lastForward));
            assertThat(reading("1-Jan-%02d".formatted(lastForward + 1)))
                    .as("one past it, which falls back a century")
                    .isEqualTo("1-Jan-%d".formatted(thisYear / 100 * 100 + lastForward - 99));
        }
    }

    @Nested
    @DisplayName("the day is checked against the month it is in")
    class TheDay {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"1-Jan-2000", "31-Jan-2000", "30-Apr-2000", "28-Feb-2001"})
        @DisplayName("a day the month has")
        void adayTheMonthHas(String literal) {
            assertThat(reading(literal)).isNotEqualTo("invalid");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"0-Jan-2000", "32-Jan-2000", "31-Apr-2000", "30-Feb-2000"})
        @DisplayName("and a day it has not")
        void adayItHasNot(String literal) {
            assertThat(reading(literal)).isEqualTo("invalid");
        }

        @Test
        @DisplayName("the twenty-ninth of February in a leap year")
        void thetwentyNinthInALeapYear() {
            assertThat(reading("29-Feb-2000")).isEqualTo("29-Feb-2000");
        }

        @ParameterizedTest(name = "29-Feb-{0}")
        @ValueSource(strings = {"2001", "1900", "2100"})
        @DisplayName("and in a year that is not one, century rule included")
        void andinAYearThatIsNot(String year) {
            assertThat(reading("29-Feb-" + year))
                    .as("1900 and 2100 are divisible by four and are not leap years")
                    .isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("the month is a number between one and twelve, or a name")
    class TheMonth {

        @ParameterizedTest(name = "month {0}")
        @ValueSource(strings = {"1-1-2000", "1-12-2000"})
        @DisplayName("one and twelve are months")
        void oneAndTwelveAreMonths(String literal) {
            assertThat(reading(literal)).isNotEqualTo("invalid");
        }

        @ParameterizedTest(name = "month {0}")
        @ValueSource(strings = {"1-0-2000", "1-13-2000"})
        @DisplayName("nought and thirteen are not")
        void noughtAndThirteenAreNot(String literal) {
            assertThat(reading(literal)).isEqualTo("invalid");
        }

        @Test
        @DisplayName("a name is matched whatever its case")
        void anameIsMatchedWhateverItsCase() {
            assertThat(reading("1-JAN-2000")).isEqualTo("1-Jan-2000");
            assertThat(reading("1-january-2000")).isEqualTo("1-Jan-2000");
        }

        @Test
        @DisplayName("and a name that is not a month is not a date")
        void anameThatIsNotAMonth() {
            assertThat(reading("1-Wibble-2000")).isNotEqualTo("1-Jan-2000");
        }
    }

    @Nested
    @DisplayName("a molded date reads back as the same date")
    class ItReadsBackTheSame {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
            "1-Jan-0000", "1-Feb-0003", "29-Feb-2000", "31-Dec-9999", "1-Jan-2000",
        })
        @DisplayName("which is what the four-digit year is for")
        void whichIsWhatTheFourDigitYearIsFor(String literal) {
            assertThat(reading(literal))
                    .as("molded as %s, a short year has to keep its padding or it "
                            + "reads back as the shorthand form and means 2003", literal)
                    .isEqualTo(literal);
        }
    }
}
