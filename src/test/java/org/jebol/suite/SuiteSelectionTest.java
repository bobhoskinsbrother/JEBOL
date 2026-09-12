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

class SuiteSelectionTest {

    private static final Path VENDORED = Path.of("src", "test", "resources", "rebol-suite");
    private static final Path REBOL_TESTS = Path.of("rebol3-source", "src", "tests");

    static boolean rebolsOwnSourceIsHere() {
        return Files.exists(REBOL_TESTS.resolve("run-tests.r3"));
    }

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

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("and every bundled module is Rebol's own module")
    void everyBundledModuleIsUnchanged() {
        Path upstream = Path.of("rebol3-source", "src", "modules");
        List<String> altered;
        try (Stream<Path> here = Files.list(VENDORED_MODULES)) {
            altered = here
                    .filter(one -> Files.exists(upstream.resolve(one.getFileName())))
                    .filter(one -> !sameBytes(one, upstream.resolve(one.getFileName())))
                    .map(one -> one.getFileName().toString())
                    .toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }

        assertThat(altered)
                .as("these differ from Rebol's own copy, so a run importing one "
                        + "would not be running what Rebol publishes:%n  %s",
                        String.join("\n  ", altered))
                .isEmpty();
    }

    private static final Path VENDORED_MODULES =
            Path.of("src", "main", "resources", "org", "jebol", "modules");

    private static boolean sameBytes(Path here, Path upstream) {
        try {
            return Files.mismatch(here, upstream) == -1;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static final Set<String> EMPTY_UPSTREAM_TOO = Set.of(
            "pair-test.r3 / pmul-3",
            "pair-test.r3 / pmul-4",
            "pair-test.r3 / pneg-4",
            "series-test.r3 / FIND/PART",
            "vector-test.r3 / Compact construction syntax (empty)",
            "vector-test.r3 / Compact construction syntax (size)");

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
