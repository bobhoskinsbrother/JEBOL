package org.jebol.domain.date;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TheDifferenceBetweenTwoDatesFromTheSourceTest {

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @ParameterizedTest(name = "difference {0} {1} is {2}")
    @CsvSource(delimiter = '|', value = {
            "9-Oct-2026/12:21:21.770902 | 9-Oct-2026/12:21:21.41201  | 0:00:00.358892",
            "9-Oct-2026/12:21:22.5      | 9-Oct-2026/12:21:21        | 0:00:01.5",
            "9-Oct-2026/12:21:21        | 9-Oct-2026/12:21:22.5      | -0:00:01.5",
            "9-Oct-2026/12:21:21.000001 | 9-Oct-2026/12:21:21        | 0:00:00.000001",
            "10-Oct-2026/0:00:00.25     | 9-Oct-2026/23:59:59.75     | 0:00:00.5",
            "9-Oct-2026/12:21:21+2:00   | 9-Oct-2026/12:21:21.5+1:00 | -1:00:00.5",
            "9-Oct-2026                 | 8-Oct-2026                 | 24:00",
    })
    @DisplayName("difference counts the time of day and its fraction, as a real 3.22.5 does")
    void theDifferenceCountsTheTimeOfDay(String later, String earlier, String answer) {
        assertThat(answerTo("difference %s %s".formatted(later, earlier))).isEqualTo(answer);
    }
}
