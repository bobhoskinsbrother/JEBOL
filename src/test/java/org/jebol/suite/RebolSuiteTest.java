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

class RebolSuiteTest {

    @BeforeAll
    static void bootOneInterpreterFirst() {
        Interpreter.create();
    }

    private static final Path SUITE =
            Path.of("src", "test", "resources", "rebol-suite");
    private static final Path GAPS = SUITE.resolve("known-gaps.txt");

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

    static Stream<SuiteFile.Assertion> assertionsExpectedToPass() {
        List<String> gaps = knownGaps();
        List<String> alsoFailingOnRebol = failingOnRebolToo();
        return everyAssertion()
                .filter(assertion -> !assertion.redOnly())
                .filter(assertion -> !gaps.contains(assertion.toString()))
                .filter(assertion -> !alsoFailingOnRebol.contains(assertion.toString()));
    }

    private static final Map<String, Verdict> OUTCOMES = new ConcurrentHashMap<>();

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

    private static void gatherReports(
            Interpreter interpreter, Map<Integer, Boolean> reports) {

        reportsNumberedBy(interpreter).forEach((which, held) ->
                reports.merge(which, held, (older, newer) -> older && newer));
    }

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

    static boolean holds(SuiteFile.Assertion assertion) {
        return verdictFor(assertion).held();
    }

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
