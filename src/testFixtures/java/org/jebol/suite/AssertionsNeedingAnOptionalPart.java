package org.jebol.suite;

import org.jebol.application.AnInterpreter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AssertionsNeedingAnOptionalPart {

    protected abstract String method();

    @BeforeAll
    static void bootOneInterpreterFirst() {
        AnInterpreter.create();
    }

    @Test
    @DisplayName("this build carries the part, and the suite has assertions that need it")
    void carriesThePartItGrades() {
        assertThat(SuiteGrading.optionalPartsThisBuildCarries()).contains(method());
        assertThat(SuiteGrading.listTheAssertionsNeedingExpectedToPass(method()).toList()).isNotEmpty();
    }

    @TestFactory
    @DisplayName("Rebol's own suite, the assertions that need this part")
    Stream<DynamicTest> holdsEveryAssertionNeedingThePart() {
        return SuiteGrading.listTheAssertionsNeedingExpectedToPass(method())
                .map(assertion -> DynamicTest.dynamicTest(assertion.toString(), () -> {
                    SuiteGrading.Verdict verdict = SuiteGrading.judge(assertion);
                    assertThat(verdict.held())
                            .as("%s%n  why: %s%n  %s", assertion, verdict.reason(), assertion.source())
                            .isTrue();
                }));
    }

    @Test
    @DisplayName("no finding about Rebol among them has quietly started passing with the part")
    void keepsNoPassingEntryInTheFindings() {
        assertThat(SuiteGrading.listTheAssertionsNeeding(method())
                .filter(assertion -> SuiteGrading.failingOnRebolToo().contains(assertion.toString()))
                .filter(SuiteGrading::holds)
                .map(SuiteFile.Assertion::toString)
                .toList())
                .as("these pass now, so the recorded finding that a real Rebol fails them too needs re-checking")
                .isEmpty();
    }
}
