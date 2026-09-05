package org.jebol.suite;

import org.jebol.domain.read.Transcoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

/**
 * Tests for the thing that decides what the suite score means.
 *
 * <p>{@link SuiteFile} slices Rebol's test scripts into the assertions
 * they make, and the whole pass count rests on it. It had no tests. That
 * showed up when a measuring tool built on it reported that a real R3
 * fails {@code even? 1.7976931348623157e308}, which R3 answers true to:
 * the number had lost digits somewhere between the file and the run.
 *
 * <p>So the property that matters is not "does it produce something" but
 * "does it produce what the file says". Each test below asks one thing,
 * and the aggregate one at the bottom asks it of all 3,556 assertions.
 */
class SuiteFileTest {

    @TempDir
    Path folder;

    private SuiteFile fileHolding(String body) {
        Path written = folder.resolve("made-up-test.r3");
        try {
            Files.writeString(written, """
                    Rebol [Title: "made up"]
                    ~~~start-file~~~ "Made up"
                    ===start-group=== "a group"
                    --test-- "a test"
                    """ + body + """

                    ===end-group===
                    ~~~end-file~~~
                    """, StandardCharsets.UTF_8);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
        return SuiteFile.read(written);
    }

    @Nested
    @DisplayName("finding the assertions")
    class Slicing {

        @Test
        void oneAssertionIsFound() {
            assertThat(fileHolding("\t--assert true").assertions()).hasSize(1);
        }

        @Test
        void twoAssertionsAreFoundSeparately() {
            assertThat(fileHolding("\t--assert true\n\t--assert false").assertions())
                    .hasSize(2);
        }

        @Test
        void aFileWithNoAssertionsYieldsNone() {
            assertThat(fileHolding("").assertions()).isEmpty();
        }

        @Test
        void theHarnessWordsAreNotThemselvesAssertions() {
            assertThat(fileHolding("\t--assert true").steps())
                    .allSatisfy(step -> assertThat(step.isAssertion()
                            || step.setup() != null).isTrue());
        }

        @Test
        void anAssertionKnowsWhichGroupItCameFrom() {
            assertThat(fileHolding("\t--assert true").assertions().getFirst().group())
                    .isEqualTo("a group");
        }

        @Test
        void anAssertionKnowsWhichTestItCameFrom() {
            assertThat(fileHolding("\t--assert true").assertions().getFirst().test())
                    .isEqualTo("a test");
        }

        @Test
        void setupBetweenAssertionsIsKeptAsAStep() {
            SuiteFile read = fileHolding("\ta: 5\n\t--assert a = 5");

            assertThat(read.steps()).anySatisfy(step ->
                    assertThat(step.isAssertion()).isFalse());
        }

        @Test
        void stepsComeBackInFileOrder() {
            SuiteFile read = fileHolding("\ta: 5\n\t--assert a = 5");
            List<SuiteFile.Step> steps = read.steps();

            int setupAt = -1;
            int assertionAt = -1;
            for (int at = 0; at < steps.size(); at++) {
                if (steps.get(at).isAssertion()) {
                    assertionAt = assertionAt < 0 ? at : assertionAt;
                } else {
                    setupAt = setupAt < 0 ? at : setupAt;
                }
            }
            assertThat(setupAt).isLessThan(assertionAt);
        }
    }

    @Nested
    @DisplayName("keeping the source the file wrote")
    class Fidelity {

        @Test
        void aDecimalKeepsEveryDigitItWasWrittenWith() {
            SuiteFile read = fileHolding("\t--assert even? 1.7976931348623157e308");

            assertThat(read.assertions().getFirst().source())
                    .contains("1.7976931348623157e308");
        }

        @Test
        void aSmallDecimalKeepsItsDigitsToo() {
            SuiteFile read = fileHolding("\t--assert 0.1234567890123456 > 0");

            assertThat(read.assertions().getFirst().source())
                    .contains("0.1234567890123456");
        }

        @Test
        void anIntegerIsUnchanged() {
            SuiteFile read = fileHolding("\t--assert 42 = 42");

            assertThat(read.assertions().getFirst().source()).contains("42");
        }

        @Test
        void aStringKeepsItsQuotes() {
            SuiteFile read = fileHolding("\t--assert \"ab\" = \"ab\"");

            assertThat(read.assertions().getFirst().source()).contains("\"ab\"");
        }

        @Test
        void aTimeIsNotTurnedIntoSomethingElse() {
            SuiteFile read = fileHolding("\t--assert 0:00:01 = 0:00:01");

            assertThat(read.assertions().getFirst().source()).contains("0:00:01");
        }

        @Test
        void aLitWordKeepsItsTick() {
            SuiteFile read = fileHolding("\t--assert 'invalid-compare = 'invalid-compare");

            assertThat(read.assertions().getFirst().source()).contains("'invalid-compare");
        }
    }

    @Nested
    @DisplayName("across the whole vendored suite")
    class EveryFile {

        @Test
        @DisplayName("every assertion's source can still be read")
        void everySourceStillReads() {
            List<String> unreadable = new java.util.ArrayList<>();

            for (SuiteFile file : RebolSuiteTest.filesInSuite()) {
                for (SuiteFile.Assertion assertion : file.assertions()) {
                    if (!Transcoder.transcode(assertion.source()).succeeded()) {
                        unreadable.add(assertion + "  ->  " + assertion.source());
                    }
                }
            }

            assertThat(unreadable)
                    .as("a source the reader refuses cannot be run, so the assertion "
                            + "scores false for a reason that is not about the assertion")
                    .isEmpty();
        }

        @Test
        @DisplayName("every assertion's source is text the file actually contains")
        void nothingIsRewrittenOnTheWayOut() {
            List<String> rewritten = new java.util.ArrayList<>();

            for (SuiteFile file : RebolSuiteTest.filesInSuite()) {
                String original = withoutWhitespace(read(
                        Path.of("src", "test", "resources", "rebol-suite", file.name())));
                for (SuiteFile.Assertion assertion : file.assertions()) {
                    String source = withoutWhitespace(assertion.source());
                    if (!source.isEmpty() && !original.contains(source)) {
                        rewritten.add(assertion + "  ->  " + assertion.source());
                    }
                }
            }

            assertThat(rewritten).isEmpty();
        }

        private static String withoutWhitespace(String text) {
            return text.replaceAll("\\s+", "");
        }

        private static String read(Path path) {
            try {
                return Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
            }
        }
    }

    /**
     * {@code --red--}, which says an assertion describes Red rather than Rebol.
     *
     * <p>In {@code rebol3-source/src/tests/quick-test-module.r3} it binds to
     * {@code as-red-only}, which sets {@code qt-red-only}; a failing assertion
     * under that flag lands in {@code qt-file-incompatible} and is reported as
     * "not like Red" rather than counted among the failures. So it marks a
     * documented difference from another language, not a defect in this one.
     *
     * <p>This was read as a no-op for a while, on the reasoning that grading
     * such an assertion anyway is merely stricter and "can only ever name a gap
     * that is really there". It cannot: {@code power 2 16} is {@code 65536.0}
     * in Rebol, so {@code integer? power 2 16} is false there too, and eight
     * entries on the gap list were asking for JEBOL to differ from the thing it
     * is measured against.
     */
    @Nested
    @DisplayName("an assertion marked as describing Red")
    class MarkedRedOnly {

        @Test
        @DisplayName("carries the mark, and an unmarked one does not")
        void carriesTheMark() {
            List<SuiteFile.Assertion> assertions = fileHolding("""
                    --assert 1 = 1
                    --red-- --assert 2 = 3""").assertions();

            assertThat(assertions).extracting(SuiteFile.Assertion::redOnly)
                    .containsExactly(false, true);
        }

        @Test
        @DisplayName("the mark covers one assertion, not the rest of the file")
        void theMarkCoversOneAssertion() {
            List<SuiteFile.Assertion> assertions = fileHolding("""
                    --red-- --assert 2 = 3
                    --assert 1 = 1""").assertions();

            assertThat(assertions).extracting(SuiteFile.Assertion::redOnly)
                    .containsExactly(true, false);
        }

        @Test
        @DisplayName("and the mark is not left in the assertion's own source")
        void theMarkIsNotInTheSource() {
            List<SuiteFile.Assertion> assertions = fileHolding("""
                    --red-- --assert 2 = 3""").assertions();

            assertThat(assertions).singleElement()
                    .extracting(SuiteFile.Assertion::source, as(STRING))
                    .isEqualTo("2 = 3");
        }

        @Test
        @DisplayName("Rebol's own files use it, and power-test is where it bites")
        void rebolsOwnFilesUseIt() {
            List<SuiteFile.Assertion> marked = RebolSuiteTest.filesInSuite().stream()
                    .flatMap(file -> file.assertions().stream())
                    .filter(SuiteFile.Assertion::redOnly)
                    .toList();

            assertThat(marked)
                    .as("every --red-- assertion the vendored suite writes")
                    .hasSize(11);
            assertThat(marked).extracting(SuiteFile.Assertion::file)
                    .containsOnly("power-test.r3", "time-test.r3", "compare-test.r3");
        }
    }
}
