package org.jebol.adapter.cli;

import org.junit.jupiter.api.DynamicTest;

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

final class RecordedRebolScript {

    private static final String WHERE_THE_ROWS_START = "foreach code [";

    private final String named;
    private final Path directory;

    RecordedRebolScript(String named, Path directory) {
        this.named = named;
        this.directory = directory;
    }

    Stream<DynamicTest> eachRowPrintsWhatRebolPrinted() throws IOException {
        String script = readFromTheClasspath("/r3-recorded/" + named + ".r3");
        List<String> rows = theRowsOf(script);
        List<String> recorded = readFromTheClasspath("/r3-recorded/" + named + ".recorded").lines().toList();
        List<String> printed = whatJebolPrintsRunning(script);
        assertThat(recorded).hasSize(rows.size());
        assertThat(printed).hasSize(rows.size());
        return IntStream.range(0, rows.size()).mapToObj(at -> DynamicTest.dynamicTest(
                rows.get(at) + " prints " + recorded.get(at),
                () -> assertThat(printed.get(at)).isEqualTo(recorded.get(at))));
    }

    private String readFromTheClasspath(String path) throws IOException {
        try (InputStream carried = getClass().getResourceAsStream(path)) {
            assertThat(carried).as("%s is not on the test classpath", path).isNotNull();
            return new String(carried.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> whatJebolPrintsRunning(String script) throws IOException {
        Path written = directory.resolve(named + ".r3");
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
}
