package org.jebol.suite;

import org.jebol.adapter.host.ProcessEnvironment;
import org.jebol.application.Bounds;
import org.jebol.application.FileSystemPort;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

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

    /**
     * Assertions a real Rebol fails as well, which are not run.
     *
     * <p>They are not gaps: JEBOL answers what the Rebol they came from
     * answers, and the assertion is wrong about that Rebol. Leaving them in
     * the gap list would say there is work here and there is not, and
     * deleting them would lose the finding, so they sit in a file of their
     * own with the {@code r3-head} output that settled each one written
     * beside it.
     */
    private static final Path FAILS_ON_REBOL_TOO =
            SUITE.resolve("fails-on-rebol-too.txt");

    static Stream<SuiteFile.Assertion> everyAssertion() {
        return filesInSuite().stream()
                .flatMap(file -> file.assertions().stream())
                .toList()
                .stream();
    }

    static List<String> knownGaps() {
        return linesOf(GAPS);
    }

    private static List<String> linesOf(Path list) {
        try {
            return Files.exists(list)
                    ? Files.readAllLines(list).stream()
                            .map(String::strip)
                            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                            .toList()
                    : List.of();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static List<String> failingOnRebolToo() {
        return linesOf(FAILS_ON_REBOL_TOO);
    }

    /**
     * What the port is held to: every assertion, less the three kinds that say
     * nothing about it.
     *
     * <p>The two lists are read from disk and are bookkeeping. The third kind
     * is read from the suite's own text: an assertion Rebol marks
     * {@code --red--} describes how Red behaves, and Rebol's runner counts a
     * failing one apart from its failures rather than as one. Deriving it from
     * the mark rather than from a list is deliberate -- a list would have to be
     * kept in step with the vendored files by hand, and the mark cannot drift
     * from the assertion it is written beside.
     */
    static Stream<SuiteFile.Assertion> assertionsExpectedToPass() {
        List<String> gaps = knownGaps();
        List<String> alsoFailingOnRebol = failingOnRebolToo();
        return everyAssertion()
                .filter(assertion -> !assertion.redOnly())
                .filter(assertion -> !gaps.contains(assertion.toString()))
                .filter(assertion -> !alsoFailingOnRebol.contains(assertion.toString()));
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
        return SuiteHost.installOn(
                Interpreter.withBounds(SuiteHost.grantingEverything()));
    }

    private static void runFile(SuiteFile file) {
        Interpreter interpreter = withAHost();
        interpreter.defineFreshWordsIn(THE_DIALECT_WORD_FOR_A_NESTED_ASSERTION);
        interpreter.run(THE_DIALECT_WORD_FOR_A_NESTED_ASSERTION);
        Map<Integer, Boolean> reports = new LinkedHashMap<>();
        Map<String, String> whyItsStepStopped = new LinkedHashMap<>();
        List<SuiteFile.Assertion> numbered = new ArrayList<>();
        for (SuiteFile.Step step : file.steps()) {
            String source = step.isAssertion() ? step.assertion().source() : step.setup();
            if (source == null || source.isBlank()) {
                continue;
            }
            Verdict verdict = new Verdict(false, "never ran");
            try {
                interpreter.defineFreshWordsIn(step.sourceToRun());
                if (step.isAssertion()) {
                    Interpreter.Step taken =
                            interpreter.runNext(step.sourceToRun());
                    verdict = taken.outcome().succeeded() && taken.outcome().value().isTruthy()
                            ? Verdict.passed()
                            : new Verdict(false, reasonFrom(taken.outcome()));
                    if (!taken.rest().isBlank()) {
                        interpreter.defineFreshWordsIn(taken.rest());
                        ScriptOutcome after = interpreter.run(taken.rest());
                        recordWhatRanInside(interpreter, step,
                                after.succeeded() ? "" : reasonFrom(after));
                    }
                } else {
                    String toRun = step.sourceToRun();
                    interpreter.defineFreshWordsIn(toRun);
                    ScriptOutcome ran = interpreter.run(toRun);
                    verdict = new Verdict(ran.succeeded(), "");
                    recordWhatRanInside(interpreter, step,
                            ran.succeeded() ? "" : reasonFrom(ran));
                }
            } catch (RuntimeException refused) {
                verdict = new Verdict(false,
                        "host exception: " + refused.getClass().getSimpleName());
                recordWhatRanInside(interpreter, step, verdict.reason());
            }
            if (step.isAssertion()) {
                OUTCOMES.put(step.assertion().toString(), verdict);
            }
            if (step.numberedSetup() != null) {
                numbered.addAll(step.nested());
                gatherReports(interpreter, reports);
                for (SuiteFile.Assertion nested : step.nested()) {
                    whyItsStepStopped.put(nested.toString(), verdict.reason());
                }
            }
        }
        gatherReports(interpreter, reports);
        for (SuiteFile.Assertion nested : numbered) {
            Boolean held = reports.get(nested.ordinal());
            String stopped = whyItsStepStopped.getOrDefault(nested.toString(), "");
            OUTCOMES.put(nested.toString(), held == null
                    ? new Verdict(false, stopped.isBlank()
                            ? "never reached: the block it is written in ended first"
                            : "never reached: the block stopped on " + stopped)
                    : held
                            ? Verdict.passed()
                            : new Verdict(false,
                                    "answered false inside the block it is written in"));
        }
    }

    /**
     * Takes whatever the nested assertions have reported since last time.
     *
     * <p>Read after every step and folded into one answer per assertion at
     * the end of the file, rather than resolved step by step. An assertion
     * written inside a function runs when the function is called, which is
     * a later step and often a much later one; reading per step threw those
     * reports away as belonging to nobody.
     */
    private static void gatherReports(
            Interpreter interpreter, Map<Integer, Boolean> reports) {

        reportsNumberedBy(interpreter).forEach((which, held) ->
                reports.merge(which, held, (older, newer) -> older && newer));
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
     *
     * <p>The other dialect words are defined too, and doing nothing is the
     * whole of their job here -- the slicer already read the group and test
     * names out of the file. Leaving them undefined meant a wrapper block that
     * held any of them died on the first one, and every assertion after it in
     * that block was never reached: 371 of them, which read as failures of the
     * port and were failures of this file.
     *
     * <p>{@code --red--} and {@code --assert-er} are the two that were still
     * missing, and they cost more than their ten uses suggest: each one killed
     * its whole file part way through.
     *
     * <p>{@code --red--} is now a harness word and marks the assertion beside
     * it, which {@link SuiteFile.Assertion#redOnly()} carries and
     * {@link #assertionsExpectedToPass()} honours. It used to be bound to a
     * no-op here, and the note beside it argued that grading such an assertion
     * anyway was merely stricter and "can only ever name a gap that is really
     * there". That was wrong, and it cost eight lines of the gap list:
     * {@code power 2 16} is {@code 65536.0} in Rebol, so
     * {@code integer? power 2 16} is false there too, and the strict reading
     * was asking JEBOL to differ from the implementation it is measured
     * against. A stricter rule is only safer when the thing it is strict about
     * is true.
     *
     * <p>{@code --assert-er} is ASSERT with an error whose id is
     * {@code feature-na} let through, and it is not counted here at all, being
     * one assertion in ten thousand and spelled differently enough that the
     * slicer never sees it. Nineteen {@code --assertf~=} assertions are missed
     * the same way; Rebol's own runner counts all three spellings.
     */
    private static final String THE_DIALECT_WORD_FOR_A_NESTED_ASSERTION = """
            jebol-nested: copy ""
            jebol-numbered: copy []
            --assert: func [result [any-type!]] [
                append jebol-nested either all [not error? :result :result] ["t"] ["f"]
                :result
            ]
            --assert-numbered: func [which [integer!] result [any-type!]] [
                repend jebol-numbered [
                    which
                    either all [not error? :result :result] [true] [false]
                ]
                :result
            ]
            ~~~start-file~~~: func [name [any-type!]] []
            ~~~end-file~~~: does []
            ===start-group===: func [name [any-type!]] []
            ===end-group===: does []
            --test--: func [name [any-type!]] []
            --red--: does []
            --assert-er: func [result [any-type!]] [:result]
            --assertf~=: func [a [any-type!] b [any-type!] c [any-type!]] [
                append jebol-nested "f"
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
    private static void recordWhatRanInside(
            Interpreter interpreter, SuiteFile.Step step, String whyItStopped) {
        if (step.nested().isEmpty()) {
            return;
        }
        if (step.numberedSetup() != null) {
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
                            : whyItStopped.isBlank()
                                    ? "never reached: the block it is written in ended first"
                                    : "never reached: the block stopped on " + whyItStopped));
        }
    }


    private static Map<Integer, Boolean> reportsNumberedBy(Interpreter interpreter) {
        String shown = interpreter.display(interpreter.run(
                "also copy jebol-numbered clear jebol-numbered"));
        Map<Integer, Boolean> answered = new LinkedHashMap<>();
        Matcher pair = NUMBERED_REPORT.matcher(shown);
        while (pair.find()) {
            answered.merge(Integer.parseInt(pair.group(1)),
                    pair.group(2).equals("true"), (older, newer) -> older && newer);
        }
        return answered;
    }

    private static final Pattern NUMBERED_REPORT =
            Pattern.compile("(\\d+) #\\((true|false)\\)");

    /**
     * The letters the nested assertions wrote, read back out of the
     * interpreter.
     *
     * <p>The string arrives molded, so it comes wrapped in delimiters that
     * have to come off. Which delimiters depends on how long it is: REBOL
     * molds a string of more than fifty characters in braces rather than in
     * quotes, and this used to accept only quotes and answer an empty string
     * for anything else.
     *
     * <p>That made a block of more than fifty assertions report every one of
     * them as never reached, however many had just passed. struct-test.r3 lost
     * all 174 of its that way while 172 of them held, and it was invisible
     * because an empty answer reads exactly like a block that ran nothing.
     *
     * <p>So an answer that is not a molded string is a fault here rather than
     * a verdict about the port, and it says so instead of returning nothing.
     */
    private static String lettersRecordedBy(Interpreter interpreter) {
        String shown = interpreter.display(
                interpreter.run("also copy jebol-nested clear jebol-nested"));
        if (shown.length() >= 2
                && ((shown.charAt(0) == '"' && shown.endsWith("\""))
                        || (shown.charAt(0) == '{' && shown.endsWith("}")))) {
            return shown.substring(1, shown.length() - 1);
        }
        throw new IllegalStateException(
                "the harness records what ran inside a block as one letter per "
                        + "assertion in a string, and asking for that string back "
                        + "gave " + shown + ", which is not one");
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
        Verdict verdict = verdictFor(assertion);
        assertThat(verdict.held())
                .as("%s%n  why: %s%n  %s",
                        assertion, verdict.reason(), assertion.source())
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

    /**
     * Every line of the gap list names an assertion that exists.
     *
     * <p>Without this the list rots in the one direction nobody looks. A line
     * comes off when the assertion it names starts passing, and an assertion
     * that no longer exists never starts passing, so a line whose wording or
     * position has shifted stays on the list for good and is counted as
     * outstanding work for ever.
     *
     * <p>It had happened to 182 of 1,016 lines by the time anybody checked --
     * 95 of them in image-test.r3 alone, which had 101 lines against 14
     * assertions. The gap list read as eighteen per cent worse than the port
     * was, and the number was quoted in the readme.
     */
    @Test
    @DisplayName("no known gap names an assertion that is not there")
    void theGapListNamesRealAssertions() {
        Set<String> live = everyAssertion()
                .map(SuiteFile.Assertion::toString)
                .collect(Collectors.toSet());

        assertThat(knownGaps().stream().filter(gap -> !live.contains(gap)).toList())
                .as("these name no assertion, so nothing can ever take them off "
                        + "the list; delete them")
                .isEmpty();
        assertThat(failingOnRebolToo().stream().filter(one -> !live.contains(one)).toList())
                .as("these name no assertion either, and a list of findings "
                        + "about assertions that are not there is not a finding")
                .isEmpty();
    }

    @Test
    @DisplayName("nothing is in both lists")
    void theTwoListsDoNotOverlap() {
        List<String> gaps = knownGaps();
        assertThat(failingOnRebolToo().stream().filter(gaps::contains).toList())
                .as("an assertion is either work to do or a finding about "
                        + "Rebol; being both means one of the two is wrong")
                .isEmpty();
    }

    /**
     * The same ratchet as {@link #theGapListHasNoPassingEntries()}, for the
     * other list.
     *
     * <p>It had none, and the two lists are not symmetrical without it. The gap
     * list only shrinks because the build fails when a listed assertion starts
     * passing; nothing asked the same of the findings list, so an entry moved
     * there left the published backlog one smaller and the gate green. That was
     * demonstrated rather than argued: moving {@code bitset-test.r3 #139},
     * which a real Rebol passes and JEBOL fails, out of one file and into the
     * other took the count down by one and broke nothing.
     *
     * <p>A finding on that list says a real Rebol does not pass this either.
     * If JEBOL starts passing it, the finding was wrong or the world moved, and
     * either way somebody has to go and look. The entries are excluded from
     * {@link #assertionsExpectedToPass()} but every one of them still runs, so
     * this costs nothing but the lookup.
     */
    @Test
    @DisplayName("no finding about Rebol has quietly started passing here")
    void theFindingsListHasNoPassingEntries() {
        List<String> findings = failingOnRebolToo();
        List<String> nowPassing = everyAssertion()
                .filter(assertion -> findings.contains(assertion.toString()))
                .filter(RebolSuiteTest::holds)
                .map(SuiteFile.Assertion::toString)
                .toList();

        assertThat(nowPassing)
                .as("these pass now, so the recorded finding that a real Rebol "
                        + "fails them too needs re-checking against ./r3-head")
                .isEmpty();
    }

    /**
     * An assertion Rebol marks {@code --red--} is never work to do.
     *
     * <p>Rebol's own runner counts a failing one apart from its failures, as a
     * difference from Red rather than a defect. Grading it here anyway is not
     * merely stricter, because the strict reading asks for behaviour a real
     * Rebol has not got: eight lines of the gap list wanted
     * {@code integer? power 2 16}, which is false in Rebol, and the ratchet
     * would have gone green for whoever made JEBOL disagree with the oracle.
     */
    @Test
    @DisplayName("no assertion about Red is on the gap list")
    void noRedOnlyAssertionIsAGap() {
        List<String> gaps = knownGaps();
        assertThat(everyAssertion()
                .filter(SuiteFile.Assertion::redOnly)
                .map(SuiteFile.Assertion::toString)
                .filter(gaps::contains)
                .toList())
                .as("Rebol reports these as differences from Red, not failures; "
                        + "fixing them would move JEBOL away from Rebol")
                .isEmpty();
    }
}
