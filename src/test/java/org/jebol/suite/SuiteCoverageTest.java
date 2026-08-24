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
                found.add(coverageOf(path, (int) written, source));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return found;
    }

    /**
     * One file's reach, and never an exception out of this method.
     *
     * <p>The reader is meant to answer a REBOL error for source it cannot
     * take in. When it throws a Java exception instead, that exception comes
     * out here during test collection, and JUnit reports it as three
     * initialisation errors with no file named -- so the one thing worth
     * knowing, which file did it, is the one thing missing. An ISO date
     * literal did exactly that: {@code 2000-01-01} reaches
     * {@code DateValue.of} as a day of 2000 and throws
     * {@code IllegalArgumentException}, and the whole run died before a
     * single assertion ran.
     *
     * <p>Caught and turned into a reading that fails for this file alone. It
     * still fails -- a file that cannot be read is never a pass -- but it
     * fails saying where.
     */
    private static Coverage coverageOf(Path path, int written, String source) {
        String name = path.getFileName().toString();
        try {
            TranscodeResult loaded = Transcoder.transcode(source);
            String note = loaded.succeeded() ? "read" : firstLineThatWillNotRead(source);
            return new Coverage(name, written, SuiteFile.read(path).assertions().size(), note);
        } catch (RuntimeException thrown) {
            return new Coverage(name, written, 0,
                    "the reader threw " + thrown.getClass().getSimpleName()
                            + " instead of answering an error: " + thrown.getMessage());
        }
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

    private static final Path STOPS = SUITE.resolve("reader-stops.txt");

    /**
     * The files the reader is known to stop partway through, and where.
     *
     * <p>Enumerated rather than counted. A recorded count is satisfied by
     * standing still: the file this replaces held a floor per file and
     * passed for as long as nothing got worse, so copy-test.r3 reached 0 of
     * its 223 assertions for weeks with a green build. Naming the file and
     * the line it stops at means the entry has to be deleted to go green
     * again, and deleting it is the fix being recorded.
     */
    private static Map<String, String> knownStops() {
        try {
            Map<String, String> recorded = new LinkedHashMap<>();
            if (!Files.exists(STOPS)) {
                return recorded;
            }
            for (String line : Files.readAllLines(STOPS)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int gap = trimmed.indexOf(' ');
                recorded.put(trimmed.substring(0, gap), trimmed.substring(gap + 1).strip());
            }
            return recorded;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("every suite file reads whole, or is listed as one that does not")
    void everyFileReadsWhole() {
        Map<String, String> known = knownStops();
        List<String> unexpected = coverage().stream()
                .filter(entry -> entry.missed() > 0)
                .filter(entry -> !known.containsKey(entry.file()))
                .map(entry -> "  %-24s reaches %d of %d  (%s)".formatted(
                        entry.file(), entry.assertionsRead(),
                        entry.assertionsInSource(), entry.note()))
                .toList();

        assertThat(unexpected)
                .as("a suite file the reader cannot take in whole, and nothing said "
                        + "so. Every assertion after the stop is invisible, and a run "
                        + "that does not count them reports a green it has not "
                        + "earned:%n%s", String.join("\n", unexpected))
                .isEmpty();
    }

    @Test
    @DisplayName("no listed file has quietly started reading whole")
    void thestopListHasNoWholeFiles() {
        List<String> nowWhole = coverage().stream()
                .filter(entry -> entry.missed() == 0)
                .map(Coverage::file)
                .filter(knownStops()::containsKey)
                .toList();

        assertThat(nowWhole)
                .as("these now read whole and are still listed as stopping. Take "
                        + "them out of reader-stops.txt; that is how the fix is "
                        + "recorded:%n  %s", String.join("\n  ", nowWhole))
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
