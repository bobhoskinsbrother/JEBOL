package org.jebol.suite;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.domain.host.HostService;
import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Rebol's own test suite, run against JEBOL.
 *
 * <p>The corpus in {@code corpus/} says what we believe REBOL does, and was
 * written from documentation. This says what the people who maintain REBOL
 * believe it does, and was written from the implementation. It is the
 * stronger of the two and it is not ours, which is the point: a case here is
 * one nobody on this side thought to try.
 *
 * <p>One JUnit case per {@code --assert}, because that is what the suite is
 * already shaped like and because a case that checks one thing says what
 * broke without needing to be read.
 *
 * <p>Most of it does not pass yet. {@code known-gaps.txt} lists what fails
 * today, so a new failure is a regression and shows up red while the backlog
 * stays visible and countable rather than being skipped into silence.
 */
class RebolSuiteTest {

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

    private static final Path SUITE =
            Path.of("src", "test", "resources", "rebol-suite");
    private static final Path GAPS = SUITE.resolve("known-gaps.txt");

    static Stream<SuiteFile.Assertion> everyAssertion() {
        return filesInSuite().stream()
                .flatMap(file -> file.assertions().stream())
                .toList()
                .stream();
    }

    static List<String> knownGaps() {
        try {
            return Files.exists(GAPS)
                    ? Files.readAllLines(GAPS).stream()
                            .map(String::strip)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .toList()
                    : List.of();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static Stream<SuiteFile.Assertion> assertionsExpectedToPass() {
        List<String> gaps = knownGaps();
        return everyAssertion().filter(assertion -> !gaps.contains(assertion.toString()));
    }

    /**
     * Every assertion's outcome, worked out one file at a time.
     *
     * <p>A test file is a script. Its assertions lean on words set up above
     * them, so each file gets one interpreter and its steps run in order,
     * setup included. Running assertions independently lost that and showed
     * up as roughly four hundred failures on words called a, b and obj.
     *
     * <p>Computed once and cached, because the parameterized test asks
     * about assertions one at a time and re-running a file per assertion
     * would be quadratic.
     *
     * <p>{@code --assert} takes one expression, and the suite often puts
     * more on the line: {@code --assert all [...] a: none} asserts the ALL
     * and then resets a. Slicing to the next dialect word takes the reset
     * with it, so the assertion is run through {@link Interpreter#runNext}
     * and whatever follows is run after it.
     *
     * <p>Two attempts to do that beside the interpreter failed first.
     * REDUCE fails wholesale when a later expression does; a hand-built
     * evaluator did not carry the same bounds or fresh-word handling. The
     * seam belonged in Interpreter.
     */
    private static final Map<String, Verdict> OUTCOMES = new ConcurrentHashMap<>();

    /**
     * Whether an assertion held, and if not, what it tripped over.
     *
     * <p>The reason is recorded here rather than worked out later, because
     * working it out later means running the assertion again, and running
     * it again on its own loses the setup the file did above it. That
     * mistake produced a work list whose top four entries were words
     * called a, s, b and v -- none of which was a real gap.
     */
    record Verdict(boolean held, String reason) {

        static Verdict passed() {
            return new Verdict(true, "");
        }
    }

    private static final Pattern MISSING_WORD =
            Pattern.compile("slot holds unset was evaluated: ([^\\s]+)");
    private static final Pattern REFINEMENT =
            Pattern.compile("([^\\s]+) has no (/[^\\s]+) refinement");

    private static String reasonFrom(ScriptOutcome outcome) {
        if (outcome.succeeded()) {
            return "answered false";
        }
        String message = outcome.value().toString();
        Matcher missing = MISSING_WORD.matcher(message);
        if (missing.find()) {
            return "no such word: " + missing.group(1);
        }
        Matcher refinement = REFINEMENT.matcher(message);
        if (refinement.find()) {
            return "no " + refinement.group(2) + " on " + refinement.group(1);
        }
        return "error " + outcome.errorId().orElse("?");
    }

    /**
     * An interpreter with the host services Rebol's own tests assume.
     *
     * <p>Those tests were written for a full host, thus a suite that
     * grants nothing measures the grant and not the port. Files are
     * confined to a directory made for the run, so a test that writes one
     * cannot reach anything the build did not make.
     */
    private static Interpreter withAHost() {
        Bounds bounds = Bounds.standard();
        for (HostService service : HostService.values()) {
            bounds = bounds.granting(service);
        }
        Interpreter interpreter = Interpreter.withBounds(bounds);
        try {
            java.nio.file.Path root = java.nio.file.Files.createTempDirectory("jebol-suite");
            layOutTheFilesTheSuiteReads(root);
            interpreter.useFileSystem(FileSystemPort.rootedAt(root));
        } catch (java.io.IOException noDirectory) {
            throw new java.io.UncheckedIOException(noDirectory);
        }
        interpreter.useEnvironment(new ProcessEnvironment());
        interpreter.useProcesses(new org.jebol.adapter.host.JavaProcesses());
        return interpreter;
    }

    /** The six files Rebol's own tests read, and read by name from the suite's tree. */
    private static final List<String> FILES_THE_SUITE_READS = List.of(
            "error.r3", "quickbrown.bin",
            "issue-2186-UTF16-BE.txt", "issue-2186-UTF16-LE.txt",
            "issue-2186-UTF32-BE.txt", "issue-2186-UTF32-LE.txt");

    /**
     * Puts those six where the tests look for them.
     *
     * <p>The run is confined to a directory made for it, which is what stops a
     * test that writes a file from reaching anything else. That also means a
     * test that *reads* one finds nothing, so twelve assertions about text
     * encodings were failing on the file being absent rather than on anything
     * JEBOL does. They are copied in rather than the root being pointed at the
     * source tree, so the confinement still holds.
     */
    private static void layOutTheFilesTheSuiteReads(java.nio.file.Path root)
            throws java.io.IOException {

        java.nio.file.Path into = root.resolve("units").resolve("files");
        java.nio.file.Files.createDirectories(into);
        for (String named : FILES_THE_SUITE_READS) {
            java.nio.file.Path from = java.nio.file.Path.of(
                    "src", "test", "resources", "rebol-suite", "units", "files", named);
            if (java.nio.file.Files.exists(from)) {
                java.nio.file.Files.copy(from, into.resolve(named));
            }
        }
    }

    private static void runFile(SuiteFile file) {
        Interpreter interpreter = withAHost();
        interpreter.defineFreshWordsIn(THE_DIALECT_WORD_FOR_A_NESTED_ASSERTION);
        interpreter.run(THE_DIALECT_WORD_FOR_A_NESTED_ASSERTION);
        for (SuiteFile.Step step : file.steps()) {
            String source = step.isAssertion() ? step.assertion().source() : step.setup();
            if (source == null || source.isBlank()) {
                continue;
            }
            Verdict verdict = new Verdict(false, "never ran");
            try {
                interpreter.defineFreshWordsIn(source);
                if (step.isAssertion()) {
                    Interpreter.Step taken = interpreter.runNext(source);
                    verdict = taken.outcome().succeeded() && taken.outcome().value().isTruthy()
                            ? Verdict.passed()
                            : new Verdict(false, reasonFrom(taken.outcome()));
                    if (!taken.rest().isBlank()) {
                        interpreter.defineFreshWordsIn(taken.rest());
                        interpreter.run(taken.rest());
                        recordWhatRanInside(interpreter, step);
                    }
                } else {
                    verdict = new Verdict(interpreter.run(source).succeeded(), "");
                    recordWhatRanInside(interpreter, step);
                }
            } catch (RuntimeException refused) {
                verdict = new Verdict(false,
                        "host exception: " + refused.getClass().getSimpleName());
            }
            if (step.isAssertion()) {
                OUTCOMES.put(step.assertion().toString(), verdict);
            }
        }
    }

    /**
     * What {@code --assert} is bound to while a setup step runs.
     *
     * <p>An assertion inside a FOREACH or an IF cannot be sliced out and run
     * on its own -- the loop variable it reads only exists while the loop is
     * running. So it is not sliced: the enclosing expression is run as it
     * stands and this records what each assertion inside it answered, which
     * is how Rebol's own harness works and the only way those assertions run
     * at all.
     *
     * <p>One letter per assertion, in the order they ran, because reading a
     * string back out of the interpreter needs no parsing and cannot be
     * confused by whatever the test itself put in a block.
     */
    private static final String THE_DIALECT_WORD_FOR_A_NESTED_ASSERTION = """
            jebol-nested: copy ""
            --assert: func [result [any-type!]] [
                append jebol-nested either all [not error? :result :result] ["t"] ["f"]
                :result
            ]""";

    /**
     * Gives each assertion inside a setup step the verdict it answered with.
     *
     * <p>An assertion in a loop body runs once per turn of the loop, and
     * there is one of it in the file. It holds when every run of it held, so
     * the letters are folded onto the assertions in order and any extra runs
     * fold onto the last one -- which is the same reading Rebol's own count
     * of thirteen thousand executions against ten thousand written implies.
     */
    private static void recordWhatRanInside(Interpreter interpreter, SuiteFile.Step step) {
        if (step.nested().isEmpty()) {
            return;
        }
        String letters = lettersRecordedBy(interpreter);
        for (int at = 0; at < step.nested().size(); at++) {
            boolean everyRunHeld = at < letters.length()
                    ? letters.charAt(at) == 't'
                    : false;
            if (at == step.nested().size() - 1 && letters.length() > step.nested().size()) {
                everyRunHeld = letters.chars().skip(at).allMatch(letter -> letter == 't');
            }
            OUTCOMES.put(step.nested().get(at).toString(), everyRunHeld
                    ? Verdict.passed()
                    : new Verdict(false, at < letters.length()
                            ? "answered false inside the block it is written in"
                            : "never reached: the block it is written in stopped first"));
        }
    }

    private static String lettersRecordedBy(Interpreter interpreter) {
        try {
            String shown = interpreter.display(
                    interpreter.run("also copy jebol-nested clear jebol-nested"));
            return shown.length() >= 2 && shown.charAt(0) == '"'
                    ? shown.substring(1, shown.length() - 1)
                    : "";
        } catch (RuntimeException unavailable) {
            return "";
        }
    }

    /** Whether JEBOL says this assertion holds. */
    static boolean holds(SuiteFile.Assertion assertion) {
        return verdictFor(assertion).held();
    }

    /** Whether it held, and what it tripped over if it did not. */
    static Verdict verdictFor(SuiteFile.Assertion assertion) {
        Verdict known = OUTCOMES.get(assertion.toString());
        if (known != null) {
            return known;
        }
        filesInSuite().stream()
                .filter(file -> file.name().equals(assertion.file()))
                .forEach(RebolSuiteTest::runFile);
        return OUTCOMES.getOrDefault(assertion.toString(),
                new Verdict(false, "the file never produced a verdict"));
    }

    static List<SuiteFile> filesInSuite() {
        try (Stream<Path> files = Files.list(SUITE)) {
            return files.filter(path -> path.toString().endsWith(".r3"))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(SuiteFile::read)
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("assertionsExpectedToPass")
    @DisplayName("Rebol's own suite")
    void theAssertionHolds(SuiteFile.Assertion assertion) {
        assertThat(holds(assertion))
                .as("%s%n  %s", assertion, assertion.source())
                .isTrue();
    }

    @Test
    @DisplayName("the suite was found and read, so this test is doing something")
    void theSuiteIsNotEmpty() {
        assertThat(everyAssertion().toList()).hasSizeGreaterThan(500);
    }

    @Test
    @DisplayName("no known gap has quietly started passing")
    void theGapListHasNoPassingEntries() {
        List<String> gaps = knownGaps();
        List<String> nowPassing = everyAssertion()
                .filter(assertion -> gaps.contains(assertion.toString()))
                .filter(RebolSuiteTest::holds)
                .map(SuiteFile.Assertion::toString)
                .toList();

        assertThat(nowPassing)
                .as("these pass now and should come off known-gaps.txt, or the "
                        + "list stops meaning anything")
                .isEmpty();
    }
}
