package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RemainingNativesAsARealRebolAnswersThemEndToEndTest {

    private static final String THE_SCRIPT = "/r3-recorded/remaining-natives.r3";

    private static final String WHAT_A_REAL_3_22_5_PRINTED = "/r3-recorded/remaining-natives.recorded";

    private static final String WHERE_THE_ROWS_START = "foreach code [";

    @TempDir
    Path directory;

    private String readFromTheClasspath(String named) throws IOException {
        try (InputStream carried = getClass().getResourceAsStream(named)) {
            assertThat(carried).as("%s is not on the test classpath", named).isNotNull();
            return new String(carried.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> whatJebolPrintsRunning(String script) throws IOException {
        Path written = directory.resolve("remaining-natives.r3");
        Files.writeString(written, script);
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        Repl.runTheCommandLine(new String[] {written.toString()},
                new BufferedReader(new StringReader("")),
                new PrintStream(printed, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                directory.toString());
        return printed.toString(StandardCharsets.UTF_8).lines().toList();
    }

    private List<String> theRowsOf(String script) {
        List<String> lines = script.lines().toList();
        int first = lines.indexOf(WHERE_THE_ROWS_START) + 1;
        return lines.subList(first, first + countOfRowsIn(lines, first)).stream()
                .map(row -> row.substring(1, row.length() - 1))
                .toList();
    }

    private int countOfRowsIn(List<String> lines, int first) {
        int count = 0;
        while (lines.get(first + count).startsWith("[")) {
            count++;
        }
        return count;
    }

    @TestFactory
    @DisplayName("each now, also, comment, to-value, trace, extension, access-os and random row prints what a real 3.22.5 printed")
    Stream<DynamicTest> eachRowPrintsWhatRebolPrinted() throws IOException {
        String script = readFromTheClasspath(THE_SCRIPT);
        List<String> rows = theRowsOf(script);
        List<String> recorded = readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED).lines().toList();
        List<String> printed = whatJebolPrintsRunning(script);
        assertThat(recorded).hasSize(rows.size());
        assertThat(printed).hasSize(rows.size());
        return IntStream.range(0, rows.size()).mapToObj(at -> DynamicTest.dynamicTest(
                rows.get(at) + " prints " + recorded.get(at),
                () -> assertThat(printed.get(at)).isEqualTo(recorded.get(at))));
    }
}
