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
     * <p>Counting lines that begin with {@code --assert} is wrong in both
     * directions and was wrong in seventeen of the sixty-seven files. It
     * misses every assertion indented inside a block, which is how
     * compare-test.r3 came to be read as having 158 when it has 269 and
     * power-test.r3 as having 4 when it has 18. It also counts
     * {@code --assert-er}, a typo on line 22 of Rebol's own image-test.r3,
     * as though it were an assertion.
     *
     * <p>Undercounting is the dangerous half. The gate asks whether the
     * reader got fewer than were written, so a denominator below the truth
     * makes the difference negative and the file exempt: eleven files could
     * not fail this gate at all, and two of them were losing assertions.
     */
    private static long assertionsWrittenIn(String source) {
        return Pattern.compile("--assert(?![A-Za-z0-9?!*+<>=~-])")
                .matcher(source)
                .results()
                .count();
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

    /**
     * What the slicer still cannot reach, as a number that must not grow.
     *
     * <p>Separate from reading, and a weaker gate on purpose. Reading is
     * absolute: a file the reader cannot take to the end fails the build with
     * no list and no excuse, and all sixty-seven now read. This is the next
     * layer -- an assertion the reader took in and the harness still does not
     * run, because it sits inside a FOREACH or an IF where it cannot be
     * sliced out and run on its own.
     *
     * <p>A ceiling rather than a list, because a list of individual
     * assertions here would be a list of things nobody can act on one at a
     * time: they all want the same fix. The ceiling only moves down.
     */
    private static final int ASSERTIONS_THE_SLICER_STILL_CANNOT_REACH = 307;

    @Test
    @DisplayName("the slicer reaches at least as many assertions as it did before")
    void theSlicerHasNotLostGround() {
        int lost = coverage().stream()
                .filter(entry -> entry.note().startsWith("read"))
                .mapToInt(Coverage::missed)
                .sum();

        assertThat(lost)
                .as("an assertion the reader took in and the harness did not run. "
                        + "It was 907 over 37 files before nested assertions were "
                        + "run in place; every one that comes back is a file whose "
                        + "count says more than it does")
                .isLessThanOrEqualTo(ASSERTIONS_THE_SLICER_STILL_CANNOT_REACH);
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
