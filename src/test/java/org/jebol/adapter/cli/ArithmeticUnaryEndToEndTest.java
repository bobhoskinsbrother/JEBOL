package org.jebol.adapter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArithmeticUnaryEndToEndTest {

    private static final String THE_PROBE = "/arithmetic/unary.r3";

    private static final String WHAT_A_REAL_3_22_5_PRINTED =
            "/arithmetic/unary.recorded";

    @TempDir
    Path directory;

    private static String readFromTheClasspath(String named) throws IOException {
        try (InputStream carried =
                     ArithmeticUnaryEndToEndTest.class.getResourceAsStream(named)) {
            assertThat(carried).as("%s is not on the test classpath", named).isNotNull();
            return new String(carried.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String whatJebolPrintsForTheProbe() throws IOException {
        Path script = directory.resolve("unary.r3");
        Files.writeString(script, readFromTheClasspath(THE_PROBE), StandardCharsets.UTF_8);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Repl.runTheCommandLine(
                new String[]{script.toString()}, output, directory.toString());
        output.flush();
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("every line JEBOL prints is the line a real 3.22.5 printed")
    void everyLineAgreesWithTheRecording() throws IOException {
        List<String> recorded =
                readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED).lines().toList();
        List<String> answered = whatJebolPrintsForTheProbe().lines().toList();

        int upToTheShorter = Math.min(recorded.size(), answered.size());
        for (int at = 0; at < upToTheShorter; at++) {
            assertThat(answered.get(at))
                    .as("line %d of %s", at + 1, THE_PROBE)
                    .isEqualTo(recorded.get(at));
        }
        assertThat(answered)
                .as("the probe asks %d questions and JEBOL answered %d; a question "
                        + "that prints nothing is one that killed the run",
                        recorded.size(), answered.size())
                .hasSameSizeAs(recorded);
    }

    @Test
    @DisplayName("the probe still asks after every datatype it claims to")
    void theProbeAsksEveryQuestionItClaimsTo() throws IOException {
        List<String> recorded =
                readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED).lines().toList();

        assertThat(recorded.stream().filter(line -> line.contains("|")).count())
                .as("the recording has lost lines; re-record it from ./r3-head")
                .isEqualTo(224);
    }
}
