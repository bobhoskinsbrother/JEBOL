package org.jebol.suite;

import org.jebol.application.AnInterpreter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class RebolSuitePart {

    static final int PARTS = 12;

    private final int part;

    RebolSuitePart(int part) {
        this.part = part;
    }

    @BeforeAll
    void bootOneInterpreterFirst() {
        AnInterpreter.create();
    }

    Stream<SuiteFile.Assertion> listAssertionsInPart() {
        return SuiteGrading.listTheAssertionsExpectedToPassInPart(part, PARTS);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listAssertionsInPart")
    @DisplayName("Rebol's own suite")
    void holdsTheAssertion(SuiteFile.Assertion assertion) {
        SuiteGrading.Verdict verdict = SuiteGrading.judge(assertion);
        assertThat(verdict.held())
                .as("%s%n  why: %s%n  %s",
                        assertion, verdict.reason(), assertion.source())
                .isTrue();
    }
}
