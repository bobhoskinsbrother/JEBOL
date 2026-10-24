package org.jebol.suite;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
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

class SuiteFileTest {

    @TempDir
    Path folder;

    private SuiteFile makeAFileHolding(String body) {
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
        void findsOneAssertion() {
            assertThat(makeAFileHolding("\t--assert true").assertions()).hasSize(1);
        }

        @Test
        void findsTwoAssertionsSeparately() {
            assertThat(makeAFileHolding("\t--assert true\n\t--assert false").assertions())
                    .hasSize(2);
        }

        @Test
        void findsNoneInAFileWithNoAssertions() {
            assertThat(makeAFileHolding("").assertions()).isEmpty();
        }

        @Test
        void skipsTheHarnessWords() {
            assertThat(makeAFileHolding("\t--assert true").steps())
                    .allSatisfy(step -> assertThat(step.isAssertion()
                            || step.setup() != null).isTrue());
        }

        @Test
        void tellsAnAssertionItsGroup() {
            assertThat(makeAFileHolding("\t--assert true").assertions().getFirst().group())
                    .isEqualTo("a group");
        }

        @Test
        void tellsAnAssertionItsTest() {
            assertThat(makeAFileHolding("\t--assert true").assertions().getFirst().test())
                    .isEqualTo("a test");
        }

        @Test
        void setupBetweenAssertionsIsKeptAsAStep() {
            SuiteFile read = makeAFileHolding("\ta: 5\n\t--assert a = 5");

            assertThat(read.steps()).anySatisfy(step ->
                    assertThat(step.isAssertion()).isFalse());
        }

        @Test
        void returnsStepsInFileOrder() {
            SuiteFile read = makeAFileHolding("\ta: 5\n\t--assert a = 5");
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
    @DisplayName("naming an assertion written inside a block")
    class NamingInsideABlock {

        @Test
        @DisplayName("takes the group and test the block opens, not the ones outside it")
        void takesTheNamesTheBlockOpens() {
            SuiteFile.Assertion inside = makeAFileHolding("""
                    if true [
                        ===start-group=== "inner group"
                        --test-- "inner test"
                        --assert true
                    ]""").assertions().getFirst();

            assertThat(List.of(inside.group(), inside.test()))
                    .containsExactly("inner group", "inner test");
        }

        @Test
        @DisplayName("keeps the outer group when the block opens only a test")
        void keepsTheOuterGroupForATestAlone() {
            SuiteFile.Assertion inside = makeAFileHolding("""
                    if true [
                        --test-- "inner test"
                        --assert true
                    ]""").assertions().getFirst();

            assertThat(List.of(inside.group(), inside.test()))
                    .containsExactly("a group", "inner test");
        }

        @Test
        @DisplayName("an assertion before the block's own test keeps the test outside it")
        void anAssertionBeforeTheInnerTestKeepsTheOuterOne() {
            List<SuiteFile.Assertion> assertions = makeAFileHolding("""
                    if true [
                        --assert true
                        --test-- "inner test"
                        --assert true
                    ]""").assertions();

            assertThat(assertions).extracting(SuiteFile.Assertion::test)
                    .containsExactly("a test", "inner test");
        }

        @Test
        @DisplayName("reaches a test opened two blocks deep")
        void reachesTwoBlocksDeep() {
            SuiteFile.Assertion inside = makeAFileHolding("""
                    if true [
                        either true [
                            --test-- "deep test"
                            --assert true
                        ][]
                    ]""").assertions().getFirst();

            assertThat(inside.test()).isEqualTo("deep test");
        }

        @Test
        @DisplayName("a block's names carry on to the assertions after it, in the order the file is written")
        void carriesOnAfterTheBlock() {
            List<SuiteFile.Assertion> assertions = makeAFileHolding("""
                    if true [
                        ===start-group=== "inner group"
                        --test-- "inner test"
                        --assert true
                    ]
                    --assert true""").assertions();

            assertThat(assertions).extracting(SuiteFile.Assertion::test)
                    .containsExactly("inner test", "inner test");
        }

        @Test
        @DisplayName("a test word with no name after it, last in its block, leaves the name as it was")
        void aTestWordWithNoNameLeavesTheNameAlone() {
            List<SuiteFile.Assertion> assertions = makeAFileHolding("""
                    if true [
                        --assert true
                        --test--
                    ]
                    --assert true""").assertions();

            assertThat(assertions).extracting(SuiteFile.Assertion::test)
                    .containsExactly("a test", "a test");
        }

        @Test
        @DisplayName("names inside a block change no assertion's number")
        void changesNoOrdinal() {
            List<SuiteFile.Assertion> assertions = makeAFileHolding("""
                    --assert true
                    if true [
                        --test-- "inner test"
                        --assert true
                        --assert true
                    ]
                    --assert true""").assertions();

            assertThat(assertions).extracting(SuiteFile.Assertion::ordinal)
                    .containsExactly(1, 2, 3, 4);
        }

        @Test
        @DisplayName("Rebol's own WAV checks, inside an if, are named for the WAV test and not the one above the if")
        void namesTheWavChecksInRebolsOwnFile() {
            SuiteFile.Assertion wav = SuiteGrading.filesInSuite().stream()
                    .filter(file -> file.name().equals("codecs-test.r3"))
                    .flatMap(file -> file.assertions().stream())
                    .filter(assertion -> assertion.ordinal() == 51)
                    .findFirst()
                    .orElseThrow();

            assertThat(List.of(wav.group(), wav.test()))
                    .containsExactly("WAV codec", "Load WAV file");
        }
    }

    @Nested
    @DisplayName("keeping the source the file wrote")
    class Fidelity {

        @Test
        void keepsEveryDigitOfADecimal() {
            SuiteFile read = makeAFileHolding("\t--assert even? 1.7976931348623157e308");

            assertThat(read.assertions().getFirst().source())
                    .contains("1.7976931348623157e308");
        }

        @Test
        void keepsTheDigitsOfASmallDecimal() {
            SuiteFile read = makeAFileHolding("\t--assert 0.1234567890123456 > 0");

            assertThat(read.assertions().getFirst().source())
                    .contains("0.1234567890123456");
        }

        @Test
        void leavesAnIntegerUnchanged() {
            SuiteFile read = makeAFileHolding("\t--assert 42 = 42");

            assertThat(read.assertions().getFirst().source()).contains("42");
        }

        @Test
        void keepsAStringsQuotes() {
            SuiteFile read = makeAFileHolding("\t--assert \"ab\" = \"ab\"");

            assertThat(read.assertions().getFirst().source()).contains("\"ab\"");
        }

        @Test
        void keepsATimeATime() {
            SuiteFile read = makeAFileHolding("\t--assert 0:00:01 = 0:00:01");

            assertThat(read.assertions().getFirst().source()).contains("0:00:01");
        }

        @Test
        void keepsALitWordsTick() {
            SuiteFile read = makeAFileHolding("\t--assert 'invalid-compare = 'invalid-compare");

            assertThat(read.assertions().getFirst().source()).contains("'invalid-compare");
        }
    }

    @Nested
    @DisplayName("across the whole vendored suite")
    class EveryFile {

        @Test
        @DisplayName("every assertion's source can still be read")
        void stillReadsEverySource() {
            List<String> unreadable = new java.util.ArrayList<>();

            for (SuiteFile file : SuiteGrading.filesInSuite()) {
                Interpreter reader = AnInterpreter.create();
                for (SuiteFile.Assertion assertion : file.assertions()) {
                    if (!reader.read(assertion.source()).succeeded()) {
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
        void rewritesNothingOnTheWayOut() {
            List<String> rewritten = new java.util.ArrayList<>();

            for (SuiteFile file : SuiteGrading.filesInSuite()) {
                String original = stripWhitespace(read(
                        Path.of("src", "testFixtures", "resources", "rebol-suite", file.name())));
                for (SuiteFile.Assertion assertion : file.assertions()) {
                    String source = stripWhitespace(assertion.source());
                    if (!source.isEmpty() && !original.contains(source)) {
                        rewritten.add(assertion + "  ->  " + assertion.source());
                    }
                }
            }

            assertThat(rewritten).isEmpty();
        }

        private static String stripWhitespace(String text) {
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

    @Nested
    @DisplayName("an assertion marked as describing Red")
    class MarkedRedOnly {

        @Test
        @DisplayName("carries the mark, and an unmarked one does not")
        void carriesTheMark() {
            List<SuiteFile.Assertion> assertions = makeAFileHolding("""
                    --assert 1 = 1
                    --red-- --assert 2 = 3""").assertions();

            assertThat(assertions).extracting(SuiteFile.Assertion::redOnly)
                    .containsExactly(false, true);
        }

        @Test
        @DisplayName("the mark covers one assertion, not the rest of the file")
        void coversOneAssertion() {
            List<SuiteFile.Assertion> assertions = makeAFileHolding("""
                    --red-- --assert 2 = 3
                    --assert 1 = 1""").assertions();

            assertThat(assertions).extracting(SuiteFile.Assertion::redOnly)
                    .containsExactly(true, false);
        }

        @Test
        @DisplayName("and the mark is not left in the assertion's own source")
        void staysOutOfTheSource() {
            List<SuiteFile.Assertion> assertions = makeAFileHolding("""
                    --red-- --assert 2 = 3""").assertions();

            assertThat(assertions).singleElement()
                    .extracting(SuiteFile.Assertion::source, as(STRING))
                    .isEqualTo("2 = 3");
        }

        @Test
        @DisplayName("Rebol's own files use it, and power-test is where it bites")
        void appearsInRebolsOwnFiles() {
            List<SuiteFile.Assertion> marked = SuiteGrading.filesInSuite().stream()
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
