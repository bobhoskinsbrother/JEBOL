package org.jebol.domain.date;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MovingADateByACountFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(delimiter = '|', value = {
            "29-Feb-2000 + 1                 | 1-Mar-2000",
            "29-Feb-2000 - 1                 | 28-Feb-2000",
            "28-Feb-2000 + 1                 | 29-Feb-2000",
            "28-Feb-2001 + 1                 | 1-Mar-2001",
            "1-Mar-2000 - 1                  | 29-Feb-2000",
            "1-Mar-2001 - 1                  | 28-Feb-2001",
            "1-Jan-2000 - 1                  | 31-Dec-1999",
            "31-Dec-1999 + 1                 | 1-Jan-2000",
            "31-Dec-2000 + 1                 | 1-Jan-2001",
            "1-Jan-2001 - 1                  | 31-Dec-2000",
            "31-Jan-2000 + 1                 | 1-Feb-2000",
            "1-Feb-2000 - 1                  | 31-Jan-2000",
            "30-Apr-2000 + 1                 | 1-May-2000",
            "1-May-2000 - 1                  | 30-Apr-2000",
            "1-Jan-2000 + 366                | 1-Jan-2001",
            "1-Jan-2001 + 365                | 1-Jan-2002",
            "1-Jan-2000 + 365                | 31-Dec-2000",
            "1-Jan-2001 + 364                | 31-Dec-2001",
            "1-Jan-1901 - 365                | 1-Jan-1900",
            "1-Jan-2000 - 366                | 31-Dec-1998",
            "1-Jan-2001 - 366                | 1-Jan-2000",
            "29-Feb-2000 + 0                 | 29-Feb-2000",
            "31/12/2003 + 1                  | 1-Jan-2004",
            "31/12/2003 - 1                  | 30-Dec-2003",
            "30/12/2003 + 1                  | 31-Dec-2003",
            "1/1/2004 - 1                    | 31-Dec-2003",
    })
    @DisplayName("a whole number moves the calendar and keeps the clock and zone")
    void movesTheCalendar(String expression, String answer) {
        assertThat(answerTo(expression)).isEqualTo(answer);
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(delimiter = '|', value = {
            "31-Jan-2003 + 1 | 1-Feb-2003",
            "28-Feb-2003 + 1 | 1-Mar-2003",
            "31-Mar-2003 + 1 | 1-Apr-2003",
            "30-Apr-2003 + 1 | 1-May-2003",
            "31-May-2003 + 1 | 1-Jun-2003",
            "30-Jun-2003 + 1 | 1-Jul-2003",
            "31-Jul-2003 + 1 | 1-Aug-2003",
            "31-Aug-2003 + 1 | 1-Sep-2003",
            "30-Sep-2003 + 1 | 1-Oct-2003",
            "31-Oct-2003 + 1 | 1-Nov-2003",
            "30-Nov-2003 + 1 | 1-Dec-2003",
            "31-Dec-2003 + 1 | 1-Jan-2004",
            "29-Feb-2004 + 1 | 1-Mar-2004",
            "1-Feb-2003 - 1  | 31-Jan-2003",
            "1-Mar-2003 - 1  | 28-Feb-2003",
            "1-Apr-2003 - 1  | 31-Mar-2003",
            "1-May-2003 - 1  | 30-Apr-2003",
            "1-Jun-2003 - 1  | 31-May-2003",
            "1-Jul-2003 - 1  | 30-Jun-2003",
            "1-Aug-2003 - 1  | 31-Jul-2003",
            "1-Sep-2003 - 1  | 31-Aug-2003",
            "1-Oct-2003 - 1  | 30-Sep-2003",
            "1-Nov-2003 - 1  | 31-Oct-2003",
            "1-Dec-2003 - 1  | 30-Nov-2003",
            "1-Jan-2004 - 1  | 31-Dec-2003",
            "1-Mar-2004 - 1  | 29-Feb-2004",
    })
    @DisplayName("every month end moves to the first of the next month and back")
    void movesAcrossEveryMonthEnd(String expression, String answer) {
        assertThat(answerTo(expression)).isEqualTo(answer);
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(delimiter = '|', value = {
            "31-Dec-1999 + 1  | 1-Jan-2000",
            "1-Jan-2000 - 1   | 31-Dec-1999",
            "31-Dec-2000 + 1  | 1-Jan-2001",
            "1-Jan-2001 - 1   | 31-Dec-2000",
            "31-Dec-1900 + 1  | 1-Jan-1901",
            "1-Jan-1901 - 1   | 31-Dec-1900",
            "31-Dec-2003 + 1  | 1-Jan-2004",
            "1-Jan-2004 - 1   | 31-Dec-2003",
            "31-Dec-16382 + 1 | 1-Jan-16383",
            "1-Jan-16383 - 1  | 31-Dec-16382",
            "1-Jan-0000 + 1   | 2-Jan-0000",
            "2-Jan-0000 - 1   | 1-Jan-0000",
    })
    @DisplayName("every year end moves to the first of the next year and back")
    void movesAcrossEveryYearEnd(String expression, String answer) {
        assertThat(answerTo(expression)).isEqualTo(answer);
    }

    @Test
    @DisplayName("every day of a leap year and the year after survives a move forward and back")
    void everyDayOfAYearRoundTrips() {
        assertThat(answerTo("""
                found: none
                repeat day 366 [
                    d: 1-Jan-2000 + (day - 1)
                    if d <> ((d + 1) - 1) [found: d]
                ]
                repeat day 365 [
                    d: 1-Jan-2001 + (day - 1)
                    if d <> ((d + 1) - 1) [found: d]
                ]
                none? found""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the clock and the zone survive the move")
    void keepsTheClockAndTheZone() {
        assertThat(answerTo("29-Feb-2000/12:34:56+2:00 + 1"))
                .isEqualTo("1-Mar-2000/12:34:56+2:00");
        assertThat(answerTo("29-Feb-2000/12:34:56+2:00 - 1"))
                .isEqualTo("28-Feb-2000/12:34:56+2:00");
        assertThat(answerTo("1-Jan-2000/12:34:56-7:00 - 1"))
                .isEqualTo("31-Dec-1999/12:34:56-7:00");
        assertThat(answerTo("31/12/2003/23:59:59+2:00 + 1"))
                .isEqualTo("1-Jan-2004/23:59:59+2:00");
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource(delimiter = '|', value = {
            "29-Feb-2000/23:59:59 + 0:00:01 | 1-Mar-2000/0:00",
            "1-Mar-2000/0:00 - 0:00:01     | 29-Feb-2000/23:59:59",
            "31-Dec-2000/23:59:59 + 0:00:01 | 1-Jan-2001/0:00",
            "1-Jan-2001/0:00 - 0:00:01     | 31-Dec-2000/23:59:59",
            "31/12/2003/23:59:59 + 0:00:01 | 1-Jan-2004/0:00",
            "1/1/2004/0:00 - 0:00:01       | 31-Dec-2003/23:59:59",
            "1-Jan-2000 + 12:00           | 1-Jan-2000/12:00",
            "1-Jan-2000 + 0.5             | 1-Jan-2000/12:00",
    })
    @DisplayName("a day is eighty-six thousand four hundred seconds, leap day included")
    void aDayIsEightySixThousandFourHundredSeconds(String expression, String answer) {
        assertThat(answerTo(expression)).isEqualTo(answer);
    }

    @Test
    @DisplayName("a move that would leave the supported years says type-limit")
    void refusesToLeaveTheSupportedYears() {
        assertThat(errorIdOf("1-Jan-0000 - 1")).isEqualTo("type-limit");
        assertThat(errorIdOf("31-Dec-16383 + 1")).isEqualTo("type-limit");
    }
}
