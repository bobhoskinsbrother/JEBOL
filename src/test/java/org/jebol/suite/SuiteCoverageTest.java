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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.BeforeAll;
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

    /**
     * One interpreter, booted before anything reads.
     *
     * <p>The reader does not build a function or a construction on its own:
     * the evaluator hands it a builder at boot, because MAKE and spec parsing
     * belong to the evaluator and the reader must not reach upward for them.
     * So a reader asked a question before any interpreter has existed answers
     * for a reader that has not been finished being built, and it refuses
     * constructs it can perfectly well read. That made every one of these
     * counts too low, and made a fix to construction syntax look like no fix
     * at all.
     */
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

    /**
     * How many assertions a file actually writes.
     *
     * <p>Counting lines that begin with {@code --assert} was wrong in both
     * directions and wrong in seventeen of the sixty-seven files: it missed
     * every assertion indented inside a block, so compare-test.r3 was read as
     * having 158 when it has 269, and where the reach exceeded that undercount
     * the difference went negative and the file could not fail this gate at
     * all.
     *
     * <p>Counting every occurrence instead was wrong the other way, because
     * a commented-out assertion is not an assertion, and neither is one
     * written inside a string. Rebol's own files carry 125 of the first --
     * 28 in csv-test.r3, 24 in vector-test.r3 -- and conditional-test.r3
     * carries the second, a whole test parked inside {@code comment { ... }}
     * against the day SWITCH/ALL exists. Counting either made a file look
     * permanently short of a target that was never there. So comments and
     * the insides of strings are dropped, and what is left is counted.
     */
    private static long assertionsWrittenIn(String source) {
        return Pattern.compile("--assert(?![A-Za-z0-9?!*+<>=~-])")
                .matcher(withoutCommentsOrStrings(source))
                .results()
                .count();
    }

    /**
     * The source with its comments and the insides of its strings removed.
     *
     * <p>A semicolon only opens a comment outside a string, so this walks the
     * text rather than cutting at the first one: {@code "a;b"} is four
     * characters of string and not the start of a comment. Braces nest and
     * quotes do not, which is the whole difference between REBOL's two string
     * forms and is why they are tracked differently here.
     */
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
