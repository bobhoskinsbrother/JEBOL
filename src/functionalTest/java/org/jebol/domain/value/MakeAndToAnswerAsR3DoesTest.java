package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MakeAndToAnswerAsR3DoesTest {

    private static final String R3_ANSWERS = "/org/jebol/make-and-to-as-r3-answers.tsv";

    private static final int EXPECTED_CASES = 2607;

    private final Interpreter interpreter = Interpreter.create();

    private String answerTo(String expression) {
        String asked = "set/any 'r try [" + expression + "] either error? :r ["
                + "rejoin [{E } r/id { } copy/part replace/all mold/all :r/arg1 newline { } 50]] ["
                + "copy/part replace/all mold/all :r newline { } 80]";
        interpreter.defineFreshWordsIn(asked);
        return Molder.form(interpreter.run(asked).value());
    }

    private List<String[]> r3Answers() throws Exception {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(R3_ANSWERS)),
                StandardCharsets.UTF_8))) {
            return lines.lines().map(line -> line.split("\t", 2)).toList();
        }
    }

    @TestFactory
    @DisplayName("make and to answer every datatype and specification as r3 does")
    Stream<DynamicTest> everyMakeAndTo() throws Exception {
        List<String[]> answers = r3Answers();
        assertThat(answers).hasSize(EXPECTED_CASES);
        return answers.stream().map(row -> DynamicTest.dynamicTest(row[0],
                () -> assertThat(answerTo(row[0])).isEqualTo(row[1])));
    }
}
