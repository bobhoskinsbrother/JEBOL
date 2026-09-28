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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArithmeticBoundariesEndToEndTest {

    private static final String THE_PROBE = "/arithmetic/boundaries.r3";

    private static final String WHAT_A_REAL_3_22_5_PRINTED =
            "/arithmetic/boundaries.recorded";

    private static final String THE_ONES_NOT_YET_RIGHT =
            "/arithmetic/boundary-gaps.txt";

    @TempDir
    Path directory;

    private static String readFromTheClasspath(String named) throws IOException {
        try (InputStream carried =
                     ArithmeticBoundariesEndToEndTest.class.getResourceAsStream(named)) {
            assertThat(carried).as("%s is not on the test classpath", named).isNotNull();
            return new String(carried.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> theKnownGaps() throws IOException {
        return readFromTheClasspath(THE_ONES_NOT_YET_RIGHT).lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    private static String thePairingOn(String line) {
        return line.contains("|") ? line.substring(0, line.indexOf('|')).strip() : "";
    }

    private String whatJebolPrintsForTheProbe() throws IOException {
        Path script = directory.resolve("boundaries.r3");
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

        Set<String> gaps = theKnownGaps();
        int upToTheShorter = Math.min(recorded.size(), answered.size());
        for (int at = 0; at < upToTheShorter; at++) {
            if (gaps.contains(thePairingOn(recorded.get(at)))) {
                assertThat(answered.get(at))
                        .as("%s answers correctly now; delete it from %s",
                                thePairingOn(recorded.get(at)), THE_ONES_NOT_YET_RIGHT)
                        .isNotEqualTo(recorded.get(at));
                continue;
            }
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
    @DisplayName("the probe still asks every boundary it claims to")
    void theProbeAsksEveryQuestionItClaimsTo() throws IOException {
        List<String> recorded =
                readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED).lines().toList();

        assertThat(recorded.stream().filter(line -> line.contains("|")).count())
                .as("the recording has lost lines; re-record it from ./r3-head")
                .isEqualTo(88);
    }
}
