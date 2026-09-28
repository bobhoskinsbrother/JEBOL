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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArithmeticEveryPairEndToEndTest {

    private static final String THE_PROBE = "/arithmetic/every-pair.r3";

    private static final String WHAT_A_REAL_3_22_5_PRINTED =
            "/arithmetic/every-pair.recorded";

    private static final String THE_ONES_NOT_YET_RIGHT = "/arithmetic/known-gaps.txt";

    @TempDir
    Path directory;

    private static String readFromTheClasspath(String named) throws IOException {
        try (InputStream carried =
                     ArithmeticEveryPairEndToEndTest.class.getResourceAsStream(named)) {
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
        Path script = directory.resolve("every-pair.r3");
        Files.writeString(script, readFromTheClasspath(THE_PROBE), StandardCharsets.UTF_8);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Repl.runTheCommandLine(
                new String[]{script.toString()}, output, directory.toString());
        output.flush();
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("every pairing that is not a known gap answers as a real 3.22.5 does")
    void everyPairingOutsideTheGapListAgrees() throws IOException {
        List<String> recorded =
                readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED).lines().toList();
        List<String> answered = whatJebolPrintsForTheProbe().lines().toList();
        Set<String> gaps = theKnownGaps();

        assertThat(answered)
                .as("the probe asks %d questions and JEBOL answered %d; a question "
                        + "that prints nothing is one that killed the run", recorded.size(),
                        answered.size())
                .hasSameSizeAs(recorded);

        for (int at = 0; at < recorded.size(); at++) {
            String pairing = thePairingOn(recorded.get(at));
            if (gaps.contains(pairing)) {
                continue;
            }
            assertThat(answered.get(at))
                    .as("line %d of %s", at + 1, THE_PROBE)
                    .isEqualTo(recorded.get(at));
        }
    }

    @Test
    @DisplayName("a gap that has been fixed must come off the list, so the list only shrinks")
    void nothingOnTheGapListQuietlyStartedWorking() throws IOException {
        List<String> recorded =
                readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED).lines().toList();
        List<String> answered = whatJebolPrintsForTheProbe().lines().toList();
        Set<String> gaps = theKnownGaps();

        List<String> nowAgreeing = new ArrayList<>();
        for (int at = 0; at < Math.min(recorded.size(), answered.size()); at++) {
            String pairing = thePairingOn(recorded.get(at));
            if (gaps.contains(pairing) && recorded.get(at).equals(answered.get(at))) {
                nowAgreeing.add(pairing);
            }
        }

        assertThat(nowAgreeing)
                .as("these answer correctly now and are still listed as gaps; "
                        + "delete them from %s", THE_ONES_NOT_YET_RIGHT)
                .isEmpty();
    }

    @Test
    @DisplayName("every listed gap names a pairing the probe actually asks")
    void theGapListHasNoStaleEntries() throws IOException {
        Set<String> asked = readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED).lines()
                .map(ArithmeticEveryPairEndToEndTest::thePairingOn)
                .filter(pairing -> !pairing.isEmpty())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        assertThat(theKnownGaps())
                .as("a gap naming no pairing can never be taken off the list")
                .isSubsetOf(asked);
    }

    @Test
    @DisplayName("the probe is the whole matrix, so a shrunken one is a shrunken test")
    void theProbeAsksEveryQuestionItClaimsTo() throws IOException {
        List<String> recorded =
                readFromTheClasspath(WHAT_A_REAL_3_22_5_PRINTED).lines().toList();

        assertThat(recorded.stream().filter(line -> line.contains("|")).count())
                .as("the recording has lost lines; re-record it from ./r3-head")
                .isEqualTo(810);
    }
}
