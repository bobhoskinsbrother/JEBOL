package org.jebol.testing;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@RunsOn(value = TestTarget.DESKTOP_LINUX, because = "it reads the repository's test sources, which are the same everywhere")
class WhereATestRunsIsSaidOneWayTest {

    private static final Set<String> NEVER_SEARCHED = Set.of("build", ".gradle", ".git", ".claude", ".kotlin",
            "rebol3-source", "test-history", "node_modules");
    private static final Pattern TEST_SOURCE_FOLDER = Pattern.compile(
            ".*/src/(unitTest|functionalTest|endToEndTest|networkTest|testFixtures|androidTest)/.*"
                    + "|.*/android/endtoend/src/.*");
    private static final Pattern ANOTHER_WAY_TO_CHOOSE_A_TARGET = Pattern.compile(
            "@(EnabledOnOs|DisabledOnOs|EnabledIf|DisabledIf|EnabledOnJre|DisabledOnJre)\\b");
    private static final Pattern AN_ASSUMPTION = Pattern.compile(
            "\\bassum(eTrue|eFalse|ingThat)\\(|\\bAssumptions\\.|TestAbortedException");
    private static final Pattern A_BRANCH_ON_THE_SYSTEM = Pattern.compile(
            "File\\.separatorChar\\s*==|getProperty\\(\"os\\.name\"[^)]*\\)\\s*\\.(startsWith|equals|contains|toLowerCase)"
                    + "|Build\\.(HARDWARE|VERSION)");
    private static final Pattern A_FIXTURE = Pattern.compile(".*/src/testFixtures/.*");
    private static final Pattern A_REASON_LEFT_EMPTY = Pattern.compile("because\\s*=\\s*\"\\s*\"");
    private static final String A_LIST_OF_WHAT_A_DEVICE_SKIPS = "not-on-a-device";

    private static List<Path> testSources;

    @BeforeAll
    static void findEveryTestSource() throws IOException {
        try (Stream<Path> walked = Files.walk(Path.of(""))) {
            testSources = walked
                    .filter(path -> !isNeverSearched(path))
                    .filter(Files::isRegularFile)
                    .filter(path -> TEST_SOURCE_FOLDER.matcher("/" + path.toString().replace('\\', '/')).matches())
                    .toList();
        }
    }

    private static boolean isNeverSearched(Path path) {
        for (Path part : path) {
            if (NEVER_SEARCHED.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> linesMatching(Pattern pattern) {
        List<String> found = new ArrayList<>();
        for (Path source : testSources) {
            if (!source.toString().endsWith(".java") && !source.toString().endsWith(".kt")) {
                continue;
            }
            List<String> lines = readLines(source);
            for (int index = 0; index < lines.size(); index++) {
                if (pattern.matcher(lines.get(index)).find() && !source.endsWith(selfPath())) {
                    found.add(source + ":" + (index + 1) + ": " + lines.get(index).strip());
                }
            }
        }
        return found;
    }

    private static Path selfPath() {
        return Path.of(WhereATestRunsIsSaidOneWayTest.class.getSimpleName() + ".java");
    }

    private static List<String> readLines(Path source) {
        try {
            return Files.readAllLines(source);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Test
    @DisplayName("the search finds the test sources of the core, the desktop, the web and Android's device suites")
    void findsTheTestSources() {
        assertThat(testSources.stream().map(path -> path.toString().replace('\\', '/')))
                .anyMatch(path -> path.startsWith("src/functionalTest/java/"))
                .anyMatch(path -> path.startsWith("desktop/src/endToEndTest/java/"))
                .anyMatch(path -> path.startsWith("web/src/"))
                .anyMatch(path -> path.startsWith("android/app/src/androidTest/java/"))
                .anyMatch(path -> path.startsWith("android/endtoend/src/main/java/"));
    }

    @Test
    @DisplayName("no test chooses where it runs with JUnit's own conditions: @RunsOn says it")
    void noJunitConditionChoosesATarget() {
        assertThat(linesMatching(ANOTHER_WAY_TO_CHOOSE_A_TARGET)).isEmpty();
    }

    @Test
    @DisplayName("no test assumes its way out of running: it runs where @RunsOn says, or fails")
    void noTestAssumesItsWayOut() {
        assertThat(linesMatching(AN_ASSUMPTION)).isEmpty();
    }

    @Test
    @DisplayName("no test branches on the system it runs on: each system's answer is a test with its own @RunsOn")
    void noTestBranchesOnTheSystem() {
        assertThat(linesMatching(A_BRANCH_ON_THE_SYSTEM).stream()
                .filter(line -> !A_FIXTURE.matcher("/" + line.replace('\\', '/')).matches())
                .toList()).isEmpty();
    }

    @Test
    @DisplayName("every @RunsOn gives its reason")
    void everyRunsOnGivesItsReason() {
        assertThat(linesMatching(A_REASON_LEFT_EMPTY)).isEmpty();
    }

    @Test
    @DisplayName("no list in a file says what a device skips")
    void noListSaysWhatADeviceSkips() {
        assertThat(testSources.stream()
                .filter(path -> path.getFileName().toString().contains(A_LIST_OF_WHAT_A_DEVICE_SKIPS))
                .toList()).isEmpty();
        assertThat(linesMatching(Pattern.compile(Pattern.quote(A_LIST_OF_WHAT_A_DEVICE_SKIPS)))).isEmpty();
    }
}
