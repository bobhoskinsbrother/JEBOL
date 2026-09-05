package org.jebol.suite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That the vendored suite is the whole suite, minus a list that says why.
 *
 * <p>Twenty-two of Rebol's seventy-six unit files were vendored and the other
 * fifty-four were not. Nothing was wrong with any measure: the suite passed,
 * the count was true, and the count was of the files that happened to be
 * there. A number that only describes what it was given cannot report what it
 * was not given, so the absence has to be checked separately or not at all.
 *
 * <p>Which is what this does. Rebol's own {@code run-tests.r3} names the files
 * it runs; that list is the authority. Every name on it is either vendored
 * here or written in {@code not-vendored.txt} with the reason. A file that
 * appears upstream and lands in neither place fails the build.
 *
 * <p>Only runs when the Rebol checkout is present, because that is a symlink
 * to somebody's working copy and not everybody has one. It is skipped rather
 * than silently passing, so the reason shows in the run.
 */
class SuiteSelectionTest {

    private static final Path VENDORED = Path.of("src", "test", "resources", "rebol-suite");
    private static final Path REBOL_TESTS = Path.of("rebol3-source", "src", "tests");

    static boolean rebolsOwnSourceIsHere() {
        return Files.exists(REBOL_TESTS.resolve("run-tests.r3"));
    }

    /** The files Rebol's own runner runs, read from the runner. */
    private static Set<String> whatRebolRuns() {
        try {
            String runner = Files.readString(
                    REBOL_TESTS.resolve("run-tests.r3"), StandardCharsets.UTF_8);
            Set<String> found = new TreeSet<>();
            for (String line : runner.lines().toList()) {
                String beforeAnyComment = line.split(";", 2)[0];
                Matcher named = Pattern
                        .compile("%units/([A-Za-z0-9_-]+\\.r3)")
                        .matcher(beforeAnyComment);
                while (named.find()) {
                    found.add(named.group(1));
                }
            }
            return found;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Set<String> vendoredHere() {
        try (Stream<Path> files = Files.list(VENDORED)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".r3"))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static Set<String> deliberatelyLeftOut() {
        try {
            Set<String> named = new TreeSet<>();
            for (String line : Files.readAllLines(VENDORED.resolve("not-vendored.txt"))) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.endsWith(".r3") || trimmed.contains(".r3 ")) {
                    named.add(trimmed.split("\\s+")[0]);
                }
            }
            return named;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("every file Rebol runs is either vendored here or written down as left out")
    void everyFileIsAccountedFor() {
        Set<String> unaccounted = new TreeSet<>(whatRebolRuns());
        unaccounted.removeAll(vendoredHere());
        unaccounted.removeAll(deliberatelyLeftOut());

        assertThat(unaccounted)
                .as("Rebol runs these and JEBOL does neither. A suite that is "
                        + "missing a file still reports a count, and the count is "
                        + "true of what it was given -- which is how 54 files went "
                        + "missing behind a green build:%n  %s",
                        String.join("\n  ", unaccounted))
                .isEmpty();
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("nothing is written down as left out and vendored at the same time")
    void nothingIsBothLeftOutAndVendored() {
        Set<String> both = new TreeSet<>(deliberatelyLeftOut());
        both.retainAll(vendoredHere());

        assertThat(both)
                .as("these are vendored and also listed as not vendored, so one of "
                        + "the two is a lie:%n  %s", String.join("\n  ", both))
                .isEmpty();
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("nothing is left out that Rebol does not run either")
    void nothingIsLeftOutThatIsNotThere() {
        Set<String> phantom = new TreeSet<>(deliberatelyLeftOut());
        phantom.removeAll(whatRebolRuns());

        List<String> upstreamHasNoRunnerEntry = List.of(
                "extension-test.r3", "port-http-test.r3", "_known-issues_.r3");
        phantom.removeAll(upstreamHasNoRunnerEntry);

        assertThat(phantom)
                .as("listed as left out, but Rebol's runner does not mention them, "
                        + "so the entry is stale:%n  %s", String.join("\n  ", phantom))
                .isEmpty();
    }

    /**
     * Every vendored file is the upstream file, byte for byte.
     *
     * <p>The tests above account for whole files and stop there, and a count of
     * files cannot report a missing line. Thirty-three assertions had been cut
     * out of nine vendored files -- fifty-five lines gone and none added -- and
     * nothing in the build could see it: {@code SuiteCoverageTest} counts the
     * vendored text against itself and reported every assertion present, which
     * was true of the text it was given.
     *
     * <p>They were cut for reasons written down at the time and kept in a
     * directory beside the suite, and the reasons went stale without anything
     * to notice. Three of the thirty-three were live failures of this port,
     * excluded on the grounds that they needed files that had since been
     * vendored; twenty-four had been excluded as needing functions that the
     * Rebol now being measured against has.
     *
     * <p>So the rule is now that a vendored file is a copy and nothing else. A
     * difference of any kind fails here, and an assertion that should not be
     * graded is named in a list where the ratchet can reach it, rather than
     * removed from the file where nothing can.
     */
    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("every vendored file is the upstream file, byte for byte")
    void everyVendoredFileIsUnchanged() {
        List<String> altered = vendoredHere().stream()
                .filter(name -> Files.exists(REBOL_TESTS.resolve("units").resolve(name)))
                .filter(name -> !sameBytes(VENDORED.resolve(name),
                        REBOL_TESTS.resolve("units").resolve(name)))
                .toList();

        assertThat(altered)
                .as("these differ from Rebol's own copy. A vendored file is a copy: "
                        + "editing one changes the measure without changing any "
                        + "number that reports on it:%n  %s",
                        String.join("\n  ", altered))
                .isEmpty();
    }

    private static boolean sameBytes(Path here, Path upstream) {
        try {
            return Files.mismatch(here, upstream) == -1;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /**
     * Tests Rebol itself leaves empty, which are not a symptom of anything.
     *
     * <p>Each has its assertions commented out upstream with a note saying why
     * -- "Not supported anymore!", "need to decide, which result is correct" --
     * so they are Rebol's own unfinished business rather than something lost in
     * the vendoring. Named rather than pattern-matched, so that a tenth one
     * appearing is a thing somebody has to look at.
     */
    private static final Set<String> EMPTY_UPSTREAM_TOO = Set.of(
            "pair-test.r3 / pmul-3",
            "pair-test.r3 / pmul-4",
            "pair-test.r3 / pneg-4",
            "series-test.r3 / FIND/PART",
            "vector-test.r3 / Compact construction syntax (empty)",
            "vector-test.r3 / Compact construction syntax (size)");

    /**
     * No test in the suite has lost the assertions it was written to make.
     *
     * <p>The byte comparison above needs Rebol's checkout to be present, and it
     * is a gitignored symlink. This asks a weaker question that needs nothing
     * but the vendored files, and it would have caught the same thirty-three:
     * cutting an assertion out leaves its {@code --test--} header standing, so
     * {@code pair-test.r3} carried nine test names and no assertions under any
     * of them.
     */
    @Test
    @DisplayName("no test has lost the assertions written under it")
    void noTestHasLostItsAssertions() {
        List<String> hollow = RebolSuiteTest.filesInSuite().stream()
                .flatMap(file -> testsAssertingNothingIn(file).stream())
                .filter(named -> !EMPTY_UPSTREAM_TOO.contains(named))
                .toList();

        assertThat(hollow)
                .as("a --test-- with nothing asserted under it is what an assertion "
                        + "cut out of a vendored file leaves behind:%n  %s",
                        String.join("\n  ", hollow))
                .isEmpty();
    }

    /**
     * Tests with no {@code --assert} written anywhere between them and the next
     * dialect word.
     *
     * <p>Read from the text rather than from the slicer, on purpose. The slicer
     * gives an assertion the last <em>top-level</em> test name, so an assertion
     * inside an {@code if} block is attributed to a name written above the
     * block, and asking it which tests own assertions calls forty innocent
     * tests empty. The text cannot be confused that way: what a cut assertion
     * leaves behind is a {@code --test--} line with the next dialect word
     * directly after it, and that is all this looks for.
     */
    private static Set<String> testsAssertingNothingIn(SuiteFile file) {
        Set<String> hollow = new TreeSet<>();
        List<String> lines = readAll(VENDORED.resolve(file.name())).lines().toList();
        String open = null;
        for (String line : lines) {
            String withoutComment = line.split(";", 2)[0];
            if (withoutComment.contains("--assert")) {
                open = null;
            } else if (withoutComment.contains(END_GROUP) || withoutComment.contains(END_FILE)) {
                if (open != null) {
                    hollow.add(file.name() + " / " + open);
                }
                open = null;
            }
            Matcher named = TEST_NAME.matcher(withoutComment);
            if (named.find()) {
                if (open != null) {
                    hollow.add(file.name() + " / " + open);
                }
                open = withoutComment.contains("--assert") ? null : named.group(1);
            }
        }
        return hollow;
    }

    private static final Pattern TEST_NAME = Pattern.compile("--test--\\s+\"([^\"]*)\"");
    private static final String END_GROUP = "===end-group===";
    private static final String END_FILE = "~~~end-file~~~";

    private static String readAll(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
