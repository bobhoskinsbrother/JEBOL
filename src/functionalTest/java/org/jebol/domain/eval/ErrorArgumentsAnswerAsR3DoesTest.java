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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorArgumentsAnswerAsR3DoesTest {

    private static final String R3_ANSWERS = "/org/jebol/error-arguments-as-r3-answers.tsv";

    private static final int EXPECTED_CASES = 1175;

    private static final String PROBE = """
            e: try [%s]
            either error? :e [
                molded: func [field /local m] [
                    m: try [mold/flat get/any in e field]
                    either error? m [
                        either all [m/id = 'expect-arg m/arg3 = end!] ["#(end)"] ["<unmoldable>"]
                    ] [m]
                ]
                print e/id
                print molded 'arg1
                print molded 'arg2
                print molded 'arg3
            ] [
                print "no-error"
                print ""
                print ""
                print ""
            ]
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
            return lines.lines().map(line -> line.split("\t", -1)).toList();
        }
    }

    @TestFactory
    @DisplayName("every error carries r3's id and arguments")
    Stream<DynamicTest> carriesArgumentsAsR3Does() throws Exception {
        List<String[]> answers = r3Answers();
        assertThat(answers).hasSize(EXPECTED_CASES);
        return answers.stream().map(row -> DynamicTest.dynamicTest(row[0],
                () -> assertThat(answerTo(row[0]))
                        .isEqualTo(Arrays.asList(row).subList(1, 5))));
    }
}
