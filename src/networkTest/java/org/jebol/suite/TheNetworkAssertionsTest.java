package org.jebol.suite;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TheNetworkAssertionsTest {

    @BeforeAll
    static void bootOneInterpreterFirst() {
        AnInterpreter.create();
    }

    static Stream<SuiteFile.Assertion> listTheAssertionsExpectedToPass() {
        return SuiteGrading.listTheNetworkAssertionsExpectedToPass();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listTheAssertionsExpectedToPass")
    @DisplayName("Rebol's own suite, the assertions that need the network")
    void holdsTheAssertion(SuiteFile.Assertion assertion) {
        SuiteGrading.Verdict verdict = SuiteGrading.judgeInTheNetworkGrading(assertion);
        assertThat(verdict.held())
                .as("%s%n  why: %s%n  %s",
                        assertion, verdict.reason(), assertion.source())
                .isTrue();
    }

    @Test
    @DisplayName("the list of assertions that need the network is not empty")
    void findsThem() {
        assertThat(SuiteGrading.listTheAssertionsNeedingTheNetwork().toList())
                .isNotEmpty();
    }

    @Test
    @DisplayName("no finding about Rebol has quietly started passing against the network")
    void keepsNoPassingEntryInTheFindings() {
        List<String> findings = SuiteGrading.failingOnRebolToo();
        List<String> nowPassing = SuiteGrading.listTheAssertionsNeedingTheNetwork()
                .filter(assertion -> findings.contains(assertion.toString()))
                .filter(assertion -> SuiteGrading.judgeInTheNetworkGrading(assertion).held())
                .map(SuiteFile.Assertion::toString)
                .toList();

        assertThat(nowPassing)
                .as("these pass now, so the recorded finding that a real Rebol "
                        + "fails them too needs re-checking against ./r3-head")
                .isEmpty();
    }
}
