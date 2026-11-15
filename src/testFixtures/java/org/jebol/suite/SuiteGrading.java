package org.jebol.suite;

import org.jebol.application.CarriedFixtures;
import org.jebol.application.Interpreter;
import org.jebol.application.PlatformUnderTest;
import org.jebol.application.ScriptOutcome;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class SuiteGrading {

    private SuiteGrading() {
    }

    enum Grading {
        EVERYTHING_BUT_THE_NETWORK,
        ONLY_THE_NETWORK
    }

    record Verdict(boolean held, String reason) {

        static Verdict passed() {
            return new Verdict(true, "");
        }
    }

    private static final Path SUITE = CarriedFixtures.directory("rebol-suite");

    private static final Path FAILS_ON_REBOL_TOO = SUITE.resolve("fails-on-rebol-too.txt");

    private static final Path NEEDS_THE_NETWORK = SUITE.resolve("needs-the-network.txt");

    private static final String DEFERRED =
            "not run here: it needs the network, which ./gradlew networkTests provides";

    private static final String WITHHELD =
            "not run here: the platform these tests run on cannot, and names it so";

    private static final Map<String, Verdict> OUTCOMES = new ConcurrentHashMap<>();

    static List<SuiteFile> filesInSuite() {
        return TheSuiteReadOncePerProcess.FILES;
    }

    static Stream<SuiteFile.Assertion> listEveryAssertion() {
        return filesInSuite().stream()
                .flatMap(file -> file.assertions().stream())
                .toList()
                .stream();
    }

    static List<String> failingOnRebolToo() {
        return linesOf(FAILS_ON_REBOL_TOO);
    }

    static Stream<SuiteFile.Assertion> listTheAssertionsExpectedToPass() {
        return listEveryAssertion()
                .filter(assertion -> !assertion.redOnly())
                .filter(assertion -> !failingOnRebolToo().contains(assertion.toString()))
                .filter(assertion -> !needsTheNetwork(assertion))
                .filter(assertion -> !isWithheldOnThisPlatform(assertion));
    }

    static Stream<SuiteFile.Assertion> listTheAssertionsNeedingTheNetwork() {
        return listEveryAssertion().filter(SuiteGrading::needsTheNetwork);
    }

    static Stream<SuiteFile.Assertion> listTheNetworkAssertionsExpectedToPass() {
        return listEveryAssertion()
                .filter(SuiteGrading::needsTheNetwork)
                .filter(assertion -> !assertion.redOnly())
                .filter(assertion -> !failingOnRebolToo().contains(assertion.toString()));
    }

    static boolean holds(SuiteFile.Assertion assertion) {
        return judge(assertion).held();
    }

    static Verdict judge(SuiteFile.Assertion assertion) {
        return judge(Grading.EVERYTHING_BUT_THE_NETWORK, assertion);
    }

    static Verdict judgeInTheNetworkGrading(SuiteFile.Assertion assertion) {
        return judge(Grading.ONLY_THE_NETWORK, assertion);
    }

    private static Verdict judge(Grading grading, SuiteFile.Assertion assertion) {
        if (grading == Grading.EVERYTHING_BUT_THE_NETWORK && needsTheNetwork(assertion)) {
            return new Verdict(false, DEFERRED);
        }
        if (grading == Grading.EVERYTHING_BUT_THE_NETWORK && isWithheldOnThisPlatform(assertion)) {
            return new Verdict(false, WITHHELD);
        }
        Verdict known = OUTCOMES.get(keyOf(grading, assertion));
        if (known != null) {
            return known;
        }
        filesInSuite().stream()
                .filter(file -> file.name().equals(assertion.file()))
                .forEach(file -> runFile(grading, file));
        return OUTCOMES.getOrDefault(keyOf(grading, assertion),
                new Verdict(false, "the file never produced a verdict"));
    }

    private static String keyOf(Grading grading, SuiteFile.Assertion assertion) {
        return grading.name() + " " + assertion;
    }

    private static boolean needsTheNetwork(SuiteFile.Assertion assertion) {
        return isNamedIn(TheNetworkEntriesReadOncePerProcess.ENTRIES, assertion);
    }

    private static boolean isNamedIn(List<NetworkEntry> entries, SuiteFile.Assertion assertion) {
        return isNamedIn(entries, assertion.file(), assertion.group(), assertion.test());
    }

    private static boolean isNamedIn(List<NetworkEntry> entries, String file, String group, String test) {
        for (NetworkEntry entry : entries) {
            if (!entry.file().equals(file)) {
                continue;
            }
            if (entry.group() == null) {
                return true;
            }
            if (entry.group().equals(group) && entry.test().equals(test)) {
                return true;
            }
        }
        return false;
    }

    static boolean isWithheldBy(List<String> named, String file, SuiteFile.Step step) {
        return isWithheldByEntries(readEntries(named), file, step);
    }

    private static boolean isWithheldByEntries(List<NetworkEntry> entries, String file, SuiteFile.Step step) {
        if (!step.isAssertion() && step.nested().isEmpty()) {
            return isNamedIn(entries, file, step.group(), step.test());
        }
        return step.isAssertion() && isNamedIn(entries, step.assertion())
                || step.nested().stream().anyMatch(assertion -> isNamedIn(entries, assertion));
    }

    private record NetworkEntry(String file, String group, String test) {
    }

    private static final class TheNetworkEntriesReadOncePerProcess {
        static final List<NetworkEntry> ENTRIES = readEntries(linesOf(NEEDS_THE_NETWORK));
    }

    private static List<NetworkEntry> readEntries(List<String> lines) {
        List<NetworkEntry> entries = new ArrayList<>();
        for (String line : lines) {
            String[] named = line.split("\\s+/\\s+", 3);
            if (named.length == 1) {
                entries.add(new NetworkEntry(named[0].strip(), null, null));
            } else if (named.length == 3) {
                entries.add(new NetworkEntry(named[0].strip(), named[1].strip(), named[2].strip()));
            }
        }
        return List.copyOf(entries);
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

    private static final String IS_ANYTHING_LEFT = "not tail? jebol-rest";

    private static final String RUN_REST_INSIDE_DO = "do jebol-rest";

    private static final String OPEN_STEP_FOR_FIRST_EXPRESSION = "jebol-rest: next jebol-step: [\n";

    private static final String RUN_FIRST_EXPRESSION_INSIDE_DO = "\n]\ndo/next jebol-step 'jebol-rest";

    private static final String OPEN_DO = "do [\n";

    private static final String CLOSE_DO = "\n]";

    private static void runFile(Grading grading, SuiteFile file) {
        Interpreter interpreter = makeAnInterpreterWithAHost();
        interpreter.defineNewWordsIn(THE_DIALECT_WORD_FOR_A_NESTED_ASSERTION);
        interpreter.run(THE_DIALECT_WORD_FOR_A_NESTED_ASSERTION);
        Map<Integer, Boolean> reports = new LinkedHashMap<>();
        Map<String, String> whyItsStepStopped = new LinkedHashMap<>();
        List<SuiteFile.Assertion> numbered = new ArrayList<>();
        for (SuiteFile.Step step : file.steps()) {
            if (grading == Grading.EVERYTHING_BUT_THE_NETWORK && theStepNeedsTheNetwork(step)) {
                deferEveryAssertionIn(grading, step);
                continue;
            }
            if (grading == Grading.EVERYTHING_BUT_THE_NETWORK && theStepIsWithheldOnThisPlatform(file.name(), step)) {
                withholdEveryAssertionIn(grading, step);
                continue;
            }
            String source = step.isAssertion() ? step.assertion().source() : step.setup();
            if (source == null || source.isBlank()) {
                continue;
            }
            Verdict verdict;
            try {
                interpreter.defineNewWordsIn(step.sourceToRun());
                if (step.isAssertion()) {
                    ScriptOutcome first = interpreter.run(OPEN_STEP_FOR_FIRST_EXPRESSION
                            + step.sourceToRun() + RUN_FIRST_EXPRESSION_INSIDE_DO);
                    verdict = first.succeeded() && first.value().isTruthy()
                            ? Verdict.passed()
                            : new Verdict(false, readTheReasonFrom(first));
                    if (interpreter.run(IS_ANYTHING_LEFT).value().isTruthy()) {
                        ScriptOutcome after = interpreter.run(RUN_REST_INSIDE_DO);
                        recordWhatRanInside(grading, interpreter, step,
                                after.succeeded() ? "" : readTheReasonFrom(after));
                    }
                } else {
                    String toRun = step.sourceToRun();
                    interpreter.defineNewWordsIn(toRun);
                    ScriptOutcome ran = interpreter.run(OPEN_DO + toRun + CLOSE_DO);
                    verdict = new Verdict(ran.succeeded(),
                            ran.succeeded() ? "" : readTheReasonFrom(ran));
                    recordWhatRanInside(grading, interpreter, step, verdict.reason());
                }
            } catch (RuntimeException refused) {
                verdict = new Verdict(false,
                        "host exception: " + refused.getClass().getSimpleName());
                recordWhatRanInside(grading, interpreter, step, verdict.reason());
            }
            if (step.isAssertion()) {
                OUTCOMES.put(keyOf(grading, step.assertion()), verdict);
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
            OUTCOMES.put(keyOf(grading, nested), held == null
                    ? new Verdict(false, stopped.isBlank()
                            ? "never reached: the block it is written in ended first"
                            : "never reached: the block stopped on " + stopped)
                    : held
                            ? Verdict.passed()
                            : new Verdict(false,
                                    "answered false inside the block it is written in"));
        }
    }

    private static boolean theStepNeedsTheNetwork(SuiteFile.Step step) {
        List<SuiteFile.Assertion> its = step.isAssertion()
                ? List.of(step.assertion())
                : step.nested();
        return !its.isEmpty() && its.stream().anyMatch(SuiteGrading::needsTheNetwork);
    }

    private static boolean theStepIsWithheldOnThisPlatform(String file, SuiteFile.Step step) {
        return isWithheldByEntries(TheWithheldEntriesReadOncePerProcess.ENTRIES, file, step);
    }

    private static void withholdEveryAssertionIn(Grading grading, SuiteFile.Step step) {
        if (step.isAssertion()) {
            OUTCOMES.put(keyOf(grading, step.assertion()), new Verdict(false, WITHHELD));
        }
        for (SuiteFile.Assertion nested : step.nested()) {
            OUTCOMES.put(keyOf(grading, nested), new Verdict(false, WITHHELD));
        }
    }

    private static boolean isWithheldOnThisPlatform(SuiteFile.Assertion assertion) {
        return TheWithheldEntriesReadOncePerProcess.ASSERTIONS.contains(assertion.toString());
    }

    private static final class TheWithheldEntriesReadOncePerProcess {
        static final List<NetworkEntry> ENTRIES = readEntries(PlatformUnderTest.current().suiteEntriesItCannotRun());
        static final Set<String> ASSERTIONS = listEveryAssertionWithheldWithItsStep(ENTRIES);
    }

    private static Set<String> listEveryAssertionWithheldWithItsStep(List<NetworkEntry> entries) {
        Set<String> withheld = new HashSet<>();
        if (entries.isEmpty()) {
            return withheld;
        }
        for (SuiteFile file : filesInSuite()) {
            for (SuiteFile.Step step : file.steps()) {
                if (isWithheldByEntries(entries, file.name(), step)) {
                    if (step.isAssertion()) {
                        withheld.add(step.assertion().toString());
                    }
                    step.nested().forEach(nested -> withheld.add(nested.toString()));
                }
            }
        }
        return Set.copyOf(withheld);
    }

    private static void deferEveryAssertionIn(Grading grading, SuiteFile.Step step) {
        if (step.isAssertion()) {
            OUTCOMES.put(keyOf(grading, step.assertion()), new Verdict(false, DEFERRED));
        }
        for (SuiteFile.Assertion nested : step.nested()) {
            OUTCOMES.put(keyOf(grading, nested), new Verdict(false, DEFERRED));
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
            jebol-step: jebol-rest: none
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
            Grading grading, Interpreter interpreter, SuiteFile.Step step,
            String whyItStopped) {
        if (step.nested().isEmpty()) {
            return;
        }
        if (step.numberedSetup() != null) {
            return;
        }
        String letters = readTheLettersRecordedBy(interpreter);
        for (int at = 0; at < step.nested().size(); at++) {
            boolean everyRunHeld = at < letters.length() && letters.charAt(at) == 't';
            if (at == step.nested().size() - 1 && letters.length() > step.nested().size()) {
                everyRunHeld = letters.chars().skip(at).allMatch(letter -> letter == 't');
            }
            OUTCOMES.put(keyOf(grading, step.nested().get(at)), everyRunHeld
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

    private static String readTheLettersRecordedBy(Interpreter interpreter) {
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

    private static final Pattern MISSING_WORD =
            Pattern.compile("slot holds unset was evaluated: ([^\\s]+)");

    private static final Pattern REFINEMENT =
            Pattern.compile("([^\\s]+) has no (/[^\\s]+) refinement");

    private static String readTheReasonFrom(ScriptOutcome outcome) {
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
        return "error " + outcome.errorId().orElse("?") + ": " + outcome.display().strip().lines().findFirst().orElse("");
    }

    private static Interpreter makeAnInterpreterWithAHost() {
        return SuiteHost.anInterpreterFor(
                SuiteHost.grantEverything());
    }

    private static final class TheSuiteReadOncePerProcess {
        static final List<SuiteFile> FILES = readEveryFileInTheSuite();
    }

    private static List<SuiteFile> readEveryFileInTheSuite() {
        try (Stream<Path> files = Files.list(SUITE)) {
            return files.filter(path -> path.toString().endsWith(".r3"))
                    .sorted(java.util.Comparator.comparing(Path::toString))
                    .map(SuiteFile::read)
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
