package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorWithNoneOnItsLeftAnswersAsR3DoesTest {

    private static final String R3_ANSWERS = "/org/jebol/operator-none-as-r3-answers.tsv";

    private static final int EXPECTED_CASES = 75;

    private static final String PROBE = """
            set/any 'r try [%s]
            either error? :r [
                print r/id
                print mold/flat r/arg1
                print mold/flat r/arg2
                print mold/flat r/arg3
            ] [
                print "value"
                print mold/flat :r
                print ""
                print ""
            ]
            """;

    private List<String> answerTo(String caseSource) {
        StringBuilder printed = new StringBuilder();
        OutputPort output = printed::append;
        AnInterpreter.createWithOutput(output).run(PROBE.formatted(caseSource));
        return Arrays.asList(printed.toString().split("\n", -1)).subList(0, 4);
    }

    private List<String[]> r3Answers() throws Exception {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(R3_ANSWERS)),
                StandardCharsets.UTF_8))) {
            return lines.lines().map(line -> line.split("\t", -1)).toList();
        }
    }

    @TestFactory
    @DisplayName("every operator answers r3's id and arguments with none on its left")
    Stream<DynamicTest> answersEveryOperatorAsR3Does() throws Exception {
        List<String[]> answers = r3Answers();
        assertThat(answers).hasSize(EXPECTED_CASES);
        return answers.stream().map(row -> DynamicTest.dynamicTest(row[0],
                () -> assertThat(answerTo(row[0]))
                        .isEqualTo(List.of(row[1], row[2], row[3], row[4]))));
    }
}
