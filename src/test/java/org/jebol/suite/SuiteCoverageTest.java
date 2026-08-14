package org.jebol.suite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How much of Rebol's own suite JEBOL can even read.
 *
 * <p>Separate from running it, because the two failures mean different
 * things. An assertion that fails is a difference in behaviour; a file that
 * will not read at all is the reader refusing REBOL that a real REBOL
 * accepts, and it hides every assertion behind it. The second is worth
 * knowing about on its own, and counting silently as zero is how it would
 * stay hidden.
 */
class SuiteCoverageTest {

    private static final Path SUITE = Path.of("src", "test", "resources", "rebol-suite");

    private record Coverage(String file, int assertionsInSource, int assertionsRead, String note) {

        int missed() {
            return assertionsInSource - assertionsRead;
        }
    }

    private static List<Coverage> coverage() {
        List<Coverage> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(SUITE)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".r3"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                String source = Files.readString(path, StandardCharsets.UTF_8);
                long written = source.lines()
                        .filter(line -> line.strip().startsWith("--assert")
                                && !line.strip().startsWith("--assertf"))
                        .count();
                TranscodeResult loaded = Transcoder.transcode(source);
                String note = loaded.succeeded() ? "read" : firstLineThatWillNotRead(source);
                found.add(new Coverage(
                        path.getFileName().toString(),
                        (int) written,
                        SuiteFile.read(path).assertions().size(),
                        note));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return found;
    }

    /**
     * Where in a file the reader stops being able to cope.
     *
     * <p>Takes the longest run of leading lines that still reads, and points
     * at the one after it. Trying lines individually does not work, because
     * a line inside a multi-line construct fails on its own for a reason
     * that is not the real one. A prefix has to be balanced to read at all,
     * so the last one that succeeds is a genuine boundary.
     */
    private static String firstLineThatWillNotRead(String source) {
        List<String> lines = source.lines().toList();
        int lastGood = 0;
        for (int upTo = 1; upTo <= lines.size(); upTo++) {
            String prefix = String.join("\n", lines.subList(0, upTo));
            if (Transcoder.transcode(prefix).succeeded()) {
                lastGood = upTo;
            }
        }
        if (lastGood >= lines.size()) {
            return "reads in prefixes but not whole";
        }
        String offending = lines.get(lastGood).strip();
        return "line " + (lastGood + 1) + ": "
                + offending.substring(0, Math.min(62, offending.length()));
    }

    private static final Path BASELINE = SUITE.resolve("reader-reach.txt");

    /** How many assertions each file reached when the baseline was taken. */
    private static Map<String, Integer> baseline() {
        try {
            Map<String, Integer> recorded = new LinkedHashMap<>();
            for (String line : Files.readAllLines(BASELINE)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+");
                recorded.put(parts[0], Integer.parseInt(parts[1]));
            }
            return recorded;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("no file reaches fewer assertions than it did before")
    void reachabilityHasNotGoneBackwards() {
        Map<String, Integer> recorded = baseline();
        List<String> regressed = coverage().stream()
                .filter(entry -> entry.assertionsRead()
                        < recorded.getOrDefault(entry.file(), 0))
                .map(entry -> "  %-24s reaches %d, used to reach %d  (%s)".formatted(
                        entry.file(), entry.assertionsRead(),
                        recorded.get(entry.file()), entry.note()))
                .toList();

        assertThat(regressed)
                .as("the reader has lost ground:%n%s",
                        String.join("\n", regressed))
                .isEmpty();
    }

    @Test
    @DisplayName("what the reader still cannot take in, as a countable backlog")
    void theRemainingBacklogIsRecorded() {
        int written = coverage().stream().mapToInt(Coverage::assertionsInSource).sum();
        int read = coverage().stream().mapToInt(Coverage::assertionsRead).sum();
        List<Coverage> incomplete = coverage().stream()
                .filter(entry -> entry.missed() > 0)
                .toList();

        System.out.printf("%nreader reaches %d of %d assertions (%d%%)%n",
                read, written, written == 0 ? 100 : read * 100 / written);
        incomplete.forEach(entry -> System.out.printf("  %-24s %4d of %4d  (%s)%n",
                entry.file(), entry.assertionsRead(), entry.assertionsInSource(),
                entry.note()));

        assertThat(read).as("the reader reaches nothing at all").isPositive();
    }
}
