package org.jebol.suite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RebolSuiteTest {

    private static final String PART_CLASS = "org.jebol.suite.RebolSuitePart%dTest";

    @Test
    @DisplayName("the suite was found and read, so this test is doing something")
    void findsAssertions() {
        assertThat(SuiteGrading.listEveryAssertion().toList()).hasSizeGreaterThan(500);
    }

    @Test
    @DisplayName("every assertion expected to pass is held by exactly one part, so splitting the suite runs each once")
    void splitsTheSuiteIntoParts() {
        List<String> inTheParts = IntStream.rangeClosed(1, RebolSuitePart.PARTS).boxed()
                .flatMap(part -> SuiteGrading.listTheAssertionsExpectedToPassInPart(part, RebolSuitePart.PARTS))
                .map(SuiteFile.Assertion::toString)
                .toList();

        assertThat(inTheParts).containsExactlyInAnyOrderElementsOf(
                SuiteGrading.listTheAssertionsExpectedToPass().map(SuiteFile.Assertion::toString).toList());
    }

    @Test
    @DisplayName("each file of the suite is in one part whole, since its assertions share one interpreter in order")
    void keepsEachFileWhole() {
        assertThat(SuiteGrading.partOfEachFile(RebolSuitePart.PARTS))
                .containsOnlyKeys(SuiteGrading.filesInSuite().stream().map(SuiteFile::name).toList())
                .allSatisfy((file, part) -> assertThat(part).isBetween(1, RebolSuitePart.PARTS));
    }

    @Test
    @DisplayName("there is a test class for each part and none past the last, so no part goes unrun")
    void hasAClassForEachPart() {
        assertThat(IntStream.rangeClosed(1, RebolSuitePart.PARTS + 1).mapToObj(RebolSuiteTest::partClass))
                .map(Optional::isPresent)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, RebolSuitePart.PARTS + 1)
                        .mapToObj(part -> part <= RebolSuitePart.PARTS)
                        .toList());
    }

    private static Optional<Class<?>> partClass(int part) {
        try {
            return Optional.of(Class.forName(PART_CLASS.formatted(part)));
        } catch (ClassNotFoundException absent) {
            return Optional.empty();
        }
    }

    @Test
    @DisplayName("no finding about Rebol has quietly started passing here")
    void keepsNoPassingEntryInTheFindings() {
        List<String> findings = SuiteGrading.failingOnRebolToo();
        List<String> nowPassing = SuiteGrading.listEveryAssertion()
                .filter(assertion -> findings.contains(assertion.toString()))
                .filter(SuiteGrading::holds)
                .map(SuiteFile.Assertion::toString)
                .toList();

        assertThat(nowPassing)
                .as("these pass now, so the recorded finding that a real Rebol "
                        + "fails them too needs re-checking against ./r3-head")
                .isEmpty();
    }
}
