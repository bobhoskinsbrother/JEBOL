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

    private static final Path VENDORED = Path.of("src", "testFixtures", "resources", "rebol-suite");
    private static final Path REBOL_TESTS = Path.of("rebol3-source", "src", "tests");

    static boolean rebolsOwnSourceIsHere() {
        return Files.exists(REBOL_TESTS.resolve("run-tests.r3"));
    }

    private static Set<String> listWhatRebolRuns() {
        try {
            String runner = org.jebol.application.TextFiles.read(REBOL_TESTS.resolve("run-tests.r3"), StandardCharsets.UTF_8);
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

    private static Set<String> listWhatIsVendoredHere() {
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
    void accountsForEveryFile() {
        Set<String> unaccounted = new TreeSet<>(listWhatRebolRuns());
        unaccounted.removeAll(listWhatIsVendoredHere());
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
    void neverBothLeavesOutAndVendors() {
        Set<String> both = new TreeSet<>(deliberatelyLeftOut());
        both.retainAll(listWhatIsVendoredHere());

        assertThat(both)
                .as("these are vendored and also listed as not vendored, so one of "
                        + "the two is a lie:%n  %s", String.join("\n  ", both))
                .isEmpty();
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("nothing is left out that Rebol does not run either")
    void leavesOutNothingThatIsNotThere() {
        Set<String> phantom = new TreeSet<>(deliberatelyLeftOut());
        phantom.removeAll(listWhatRebolRuns());

        List<String> upstreamHasNoRunnerEntry = List.of(
                "extension-test.r3", "port-http-test.r3", "_known-issues_.r3");
        phantom.removeAll(upstreamHasNoRunnerEntry);

        assertThat(phantom)
                .as("listed as left out, but Rebol's runner does not mention them, "
                        + "so the entry is stale:%n  %s", String.join("\n  ", phantom))
                .isEmpty();
    }

    private record NetworkEntry(String file, String group, String test) {
    }

    private static List<NetworkEntry> needsTheNetwork() {
        try {
            List<NetworkEntry> entries = new java.util.ArrayList<>();
            for (String line : Files.readAllLines(
                    VENDORED.resolve("needs-the-network.txt"), StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] named = trimmed.split("\\s*/\\s*", 3);
                if (named.length == 1) {
                    entries.add(new NetworkEntry(named[0].strip(), null, null));
                } else if (named.length == 3) {
                    entries.add(new NetworkEntry(
                            named[0].strip(), named[1].strip(), named[2].strip()));
                }
            }
            return entries;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("every entry that needs the network names something the suite still has")
    void everyNetworkEntryNamesSomethingThatIsThere() {
        List<String> stale = new java.util.ArrayList<>();
        for (NetworkEntry entry : needsTheNetwork()) {
            SuiteFile file = SuiteGrading.filesInSuite().stream()
                    .filter(each -> each.name().equals(entry.file()))
                    .findFirst()
                    .orElse(null);
            if (file == null) {
                stale.add(entry.file() + " names no suite file");
                continue;
            }
            if (entry.group() != null && file.assertions().stream().noneMatch(assertion ->
                    assertion.group().equals(entry.group())
                            && assertion.test().equals(entry.test()))) {
                stale.add(entry.file() + " / " + entry.group() + " / " + entry.test()
                        + " names no test in that file");
            }
        }

        assertThat(needsTheNetwork())
                .as("with no entries the checks below hold vacuously")
                .isNotEmpty();
        assertThat(stale)
                .as("a list of assertions that need the network, one of which no "
                        + "longer exists, would hold a line for ever and hide the "
                        + "next file that starts reaching out:%n  %s",
                        String.join("\n  ", stale))
                .isEmpty();
    }

    @Test
    @DisplayName("nothing that needs the network is graded on the build that has none")
    void gradesNothingThatNeedsTheNetworkOnEveryBuild() {
        List<String> graded = SuiteGrading.listTheAssertionsNeedingTheNetwork()
                .filter(SuiteGrading::holds)
                .map(SuiteFile.Assertion::toString)
                .toList();

        assertThat(graded)
                .as("these run on a build that promises to need nothing but a JDK, "
                        + "so a machine with no network changes what the build says:%n  %s",
                        String.join("\n  ", graded))
                .isEmpty();
    }

    private static final Set<String> REBOLS_OPTIONAL_COMPRESSORS =
            Set.of("br", "crush", "lz4", "lzav", "lzma", "lzw");

    @Test
    @DisplayName("every entry that needs an optional part names one of R3's six and something the suite still has")
    void everyOptionalPartEntryNamesSomethingThatIsThere() {
        List<String> stale = SuiteGrading.optionalPartEntries().stream()
                .filter(entry -> !REBOLS_OPTIONAL_COMPRESSORS.contains(entry.method())
                        || SuiteGrading.filesInSuite().stream().noneMatch(entry::namesAnyOf))
                .map(entry -> entry.method() + "  " + entry.place())
                .toList();

        assertThat(SuiteGrading.optionalPartEntries())
                .as("with no entries the checks below hold vacuously")
                .isNotEmpty();
        assertThat(stale)
                .as("these name no compressor R3 leaves out of its Core product, or "
                        + "nothing in the suite, so they would hold a line for ever:%n  %s",
                        String.join("\n  ", stale))
                .isEmpty();
    }

    @Test
    @DisplayName("nothing named as needing an optional part passes on a build without it")
    void passesNothingThatNeedsAnAbsentPart() {
        List<String> findings = SuiteGrading.failingOnRebolToo();
        List<String> passing = SuiteGrading.listTheAssertionsNeedingAnOptionalPartThisBuildLacks()
                .filter(assertion -> !findings.contains(assertion.toString()))
                .filter(SuiteGrading::holds)
                .map(SuiteFile.Assertion::toString)
                .toList();

        assertThat(passing)
                .as("these pass without the part they are listed under, so they do "
                        + "not need it and belong in the core's own run:%n  %s",
                        String.join("\n  ", passing))
                .isEmpty();
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("every vendored file is the upstream file, byte for byte, unless it is registered as edited here")
    void keepsEveryVendoredFileUnchanged() {
        List<String> altered = listWhatIsVendoredHere().stream()
                .filter(name -> Files.exists(upstreamOf(name)))
                .filter(name -> !editedHere().contains(name))
                .filter(name -> !haveTheSameBytes(VENDORED.resolve(name), upstreamOf(name)))
                .toList();

        assertThat(altered)
                .as("these differ from Rebol's own copy. A vendored file is a copy: "
                        + "editing one changes the measure without changing any "
                        + "number that reports on it, so an edit is registered in "
                        + "edited-here.txt with its reason:%n  %s",
                        String.join("\n  ", altered))
                .isEmpty();
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("every file registered as edited here is vendored, and really differs from upstream")
    void registersOnlyRealEdits() {
        List<String> stale = editedHere().stream()
                .filter(name -> !Files.exists(VENDORED.resolve(name))
                        || !Files.exists(upstreamOf(name))
                        || haveTheSameBytes(VENDORED.resolve(name), upstreamOf(name)))
                .toList();

        assertThat(editedHere())
                .as("with no entries the check below holds vacuously")
                .isNotEmpty();
        assertThat(stale)
                .as("registered as edited, but missing or identical to Rebol's own "
                        + "copy, so the entry is stale:%n  %s", String.join("\n  ", stale))
                .isEmpty();
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("a file edited here keeps every assertion upstream has: its text, group, test and number")
    void editsKeepEveryAssertionInPlace() {
        List<String> drifted = editedHere().stream()
                .filter(name -> !listAssertionsOf(VENDORED.resolve(name))
                        .equals(listAssertionsOf(upstreamOf(name))))
                .toList();

        assertThat(drifted)
                .as("an edit here may only move where a block begins and ends; these "
                        + "changed an assertion, its heading or its number, which every "
                        + "list keyed by that number would silently follow:%n  %s",
                        String.join("\n  ", drifted))
                .isEmpty();
    }

    private static Path upstreamOf(String name) {
        return REBOL_TESTS.resolve("units").resolve(name);
    }

    private static List<String> listAssertionsOf(Path file) {
        List<String> placed = SuiteFile.read(file).assertions().stream()
                .map(SuiteFile.Assertion::toString)
                .toList();
        List<String> written = readAll(file).lines()
                .map(line -> line.split(";", 2)[0].strip())
                .filter(line -> line.contains("--assert"))
                .toList();
        return java.util.stream.Stream.concat(placed.stream(), written.stream()).toList();
    }

    private static Set<String> editedHere() {
        try {
            Set<String> named = new TreeSet<>();
            for (String line : Files.readAllLines(VENDORED.resolve("edited-here.txt"))) {
                if (line.isBlank() || line.startsWith("#") || Character.isWhitespace(line.charAt(0))) {
                    continue;
                }
                named.add(line.split("\\s+")[0]);
            }
            return named;
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("an entry needing an optional part may name one assertion by its number, and then names no other")
    void optionalPartEntryCanNameOneAssertion() {
        SuiteGrading.OptionalPartEntry entry =
                new SuiteGrading.OptionalPartEntry("lzma", "codecs-test.r3 / SWF codec / Load SWF file #80");

        List<String> named = SuiteGrading.filesInSuite().stream()
                .filter(file -> file.name().equals("codecs-test.r3"))
                .flatMap(file -> file.assertions().stream())
                .filter(entry::names)
                .map(SuiteFile.Assertion::toString)
                .toList();

        assertThat(named).containsExactly("codecs-test.r3 / SWF codec / Load SWF file #80");
    }

    @Test
    @EnabledIf("rebolsOwnSourceIsHere")
    @DisplayName("and every bundled module is Rebol's own module")
    void keepsEveryBundledModuleUnchanged() {
        Path upstream = Path.of("rebol3-source", "src", "modules");
        List<String> altered;
        try (Stream<Path> here = Files.list(VENDORED_MODULES)) {
            altered = here
                    .filter(one -> Files.exists(upstream.resolve(one.getFileName())))
                    .filter(one -> !REPAIRED_BECAUSE_UPSTREAM_CANNOT_LOAD_IT
                            .contains(one.getFileName().toString()))
                    .filter(one -> !haveTheSameBytes(one, upstream.resolve(one.getFileName())))
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

    private static final Set<String> REPAIRED_BECAUSE_UPSTREAM_CANNOT_LOAD_IT =
            Set.of("unicode-utils.reb");

    private static boolean haveTheSameBytes(Path here, Path upstream) {
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
    void losesNoTestsAssertions() {
        List<String> hollow = SuiteGrading.filesInSuite().stream()
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
            return org.jebol.application.TextFiles.read(path, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
