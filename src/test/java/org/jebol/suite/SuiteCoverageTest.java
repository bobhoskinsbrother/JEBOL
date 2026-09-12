package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SuiteCoverageTest {

    @BeforeAll
    static void bootOneInterpreterFirst() {
        Interpreter.create();
    }

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
                long written = assertionsWrittenIn(source);
                found.add(coverageOf(path, (int) written, source));
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return found;
    }

    private static long assertionsWrittenIn(String source) {
        return Pattern.compile("--assert(?![A-Za-z0-9?!*+<>=~-])")
                .matcher(withoutCommentsOrStrings(source))
                .results()
                .count();
    }

    private static String withoutCommentsOrStrings(String source) {
        StringBuilder kept = new StringBuilder(source.length());
        boolean inQuotes = false;
        int braces = 0;
        boolean commented = false;
        for (int at = 0; at < source.length(); at++) {
            char letter = source.charAt(at);
            if (letter == '\n') {
                commented = false;
                kept.append(letter);
                continue;
            }
            if (commented) {
                continue;
            }
            if (!inQuotes && braces == 0 && letter == ';') {
                commented = true;
                continue;
            }
            if (letter == '^' && at + 1 < source.length()) {
                at++;
                continue;
            }
            boolean wasInside = inQuotes || braces > 0;
            if (braces == 0 && letter == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && letter == '{') {
                braces++;
            } else if (!inQuotes && letter == '}' && braces > 0) {
                braces--;
            } else if (!wasInside) {
                kept.append(letter);
            }
        }
        return kept.toString();
    }

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

    @Test
    @DisplayName("every suite file is read to the end")
    void everyFileIsReadToTheEnd() {
        List<String> short_ = coverage().stream()
                .filter(entry -> !entry.note().startsWith("read"))
                .map(entry -> "  %-26s reaches %4d of %4d   %s".formatted(
                        entry.file(), entry.assertionsRead(),
                        entry.assertionsInSource(), entry.note()))
                .toList();

        assertThat(short_)
                .as("a suite file that is not read to the end. There is no list to "
                        + "add it to and no count that makes it acceptable: every "
                        + "assertion past the stop is invisible, a run that does not "
                        + "count them reports a green it has not earned, and two "
                        + "weeks went into chasing errors that were sitting behind "
                        + "exactly this. Read the file or take it out of the suite "
                        + "and say so in not-vendored.txt.%n%s",
                        String.join("\n", short_))
                .isEmpty();
    }

    @Test
    @DisplayName("every assertion a file writes is one the harness runs")
    void everyAssertionWrittenIsRun() {
        List<String> lost = coverage().stream()
                .filter(entry -> entry.missed() > 0)
                .map(entry -> "  %-26s reaches %4d of %4d".formatted(
                        entry.file(), entry.assertionsRead(), entry.assertionsInSource()))
                .toList();

        assertThat(lost)
                .as("an assertion written in a suite file that the harness does not "
                        + "run. There is no list to add it to. It was 907 over 37 "
                        + "files when only top-level assertions were sliced, and an "
                        + "assertion nobody runs is one nobody can be told about:%n%s",
                        String.join("\n", lost))
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
