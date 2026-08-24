package org.jebol.suite;

import static org.assertj.core.api.Assertions.assertThat;

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

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
                "extension-test.r3", "integer-test_.r3",
                "port-http-test.r3", "_known-issues_.r3");
        phantom.removeAll(upstreamHasNoRunnerEntry);

        assertThat(phantom)
                .as("listed as left out, but Rebol's runner does not mention them, "
                        + "so the entry is stale:%n  %s", String.join("\n  ", phantom))
                .isEmpty();
    }
}
