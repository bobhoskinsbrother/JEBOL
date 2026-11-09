package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class NearAndWhereAnswerAsR3DoesTest {

    private static final String R3_ANSWERS = "/org/jebol/near-and-where-as-r3-answers.tsv";

    private static final int EXPECTED_CASES = 622;

    private static final String PROBE = """
            e: try [%s]
            w: either all [block? e/where p: find e/where 'try] [copy/part e/where next p] [e/where]
            print e/id
            print mold/flat e/near
            print mold/flat w
            """;

    private List<String> answerTo(String caseSource) {
        List<String> lines = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        OutputPort output = text -> {
            pending.append(text);
            int newline;
            while ((newline = pending.indexOf("\n")) >= 0) {
                lines.add(pending.substring(0, newline));
                pending.delete(0, newline + 1);
            }
        };
        Interpreter interpreter = AnInterpreter.createWithOutput(output);
        interpreter.run(PROBE.formatted(caseSource));
        return lines;
    }

    private List<String[]> r3Answers() throws Exception {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(R3_ANSWERS)),
                StandardCharsets.UTF_8))) {
            return lines.lines().map(line -> line.split("\t")).toList();
        }
    }

    @TestFactory
    @DisplayName("every refusal answers r3's id, near and where")
    Stream<DynamicTest> locatesEveryRefusalAsR3Does() throws Exception {
        List<String[]> answers = r3Answers();
        assertThat(answers).hasSize(EXPECTED_CASES);
        return answers.stream().map(row -> DynamicTest.dynamicTest(row[0],
                () -> assertThat(answerTo(row[0]))
                        .isEqualTo(List.of(row[1], row[2], row[3]))));
    }
}
