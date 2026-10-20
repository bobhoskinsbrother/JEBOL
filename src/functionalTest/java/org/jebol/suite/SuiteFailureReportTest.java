package org.jebol.suite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SuiteFailureReportTest {

    private static String readTheReasonFor(SuiteFile.Assertion assertion) {
        return SuiteGrading.judge(assertion).reason();
    }

    @Test
    @DisplayName("the assertions that run and answer false, by group")
    void groupsTheQuietDisagreements() {
        Map<String, Integer> byGroup = new LinkedHashMap<>();
        Map<String, String> anExample = new LinkedHashMap<>();

        for (SuiteFile.Assertion assertion : SuiteGrading.listEveryAssertion().toList()) {
            if (SuiteGrading.holds(assertion) || !"answered false".equals(readTheReasonFor(assertion))) {
                continue;
            }
            String group = assertion.file() + " / " + assertion.group();
            byGroup.merge(group, 1, Integer::sum);
            anExample.putIfAbsent(group, assertion.source());
        }

        System.out.printf("%n%d groups answering false:%n", byGroup.size());
        byGroup.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(24)
                .forEach(entry -> System.out.printf("  %4d  %s%n        e.g. %s%n",
                        entry.getValue(), entry.getKey(),
                        anExample.get(entry.getKey()).replace("\n", " ").substring(
                                0, Math.min(96, anExample.get(entry.getKey()).length()))));
    }

    @Test
    @DisplayName("several examples from the two largest groups")
    void samplesTheLargestGroups() {
        Map<String, List<String>> examples = new LinkedHashMap<>();
        for (SuiteFile.Assertion assertion : SuiteGrading.listEveryAssertion().toList()) {
            if (SuiteGrading.holds(assertion)) {
                continue;
            }
            examples.computeIfAbsent(assertion.file() + " / " + assertion.group(),
                    key -> new java.util.ArrayList<>()).add(assertion.source());
        }
        examples.entrySet().stream()
                .sorted((left, right) -> right.getValue().size() - left.getValue().size())
                .limit(5)
                .forEach(entry -> {
                    System.out.printf("%n%s (%d)%n", entry.getKey(), entry.getValue().size());
                    entry.getValue().stream().limit(10).forEach(source ->
                            System.out.println("    " + source.replace("\n", " ")));
                });
    }

    @Test
    @DisplayName("how many steps each file has, and how many are setup")
    void countsSteps() {
        SuiteGrading.filesInSuite().stream().limit(6).forEach(file ->
                System.out.printf("  %-24s %4d steps, %4d assertions, %4d setup%n",
                        file.name(), file.steps().size(), file.assertions().size(),
                        file.steps().stream().filter(step -> !step.isAssertion()).count()));
    }

    @Test
    @DisplayName("every assertion that escaped as a Java exception, named")
    void namesTheHostExceptions() {
        List<SuiteFile.Assertion> escaping = SuiteGrading.listEveryAssertion()
                .filter(assertion -> !SuiteGrading.holds(assertion))
                .filter(assertion -> readTheReasonFor(assertion).startsWith("host exception"))
                .toList();

        System.out.printf("%n%d assertions escaped as a host exception:%n", escaping.size());
        escaping.forEach(assertion -> System.out.printf("  %-34s %s%n      %s%n",
                readTheReasonFor(assertion),
                assertion.file() + " / " + assertion.group(),
                assertion.source().replace("\n", " ")));
    }

    @Test
    @DisplayName("every failing assertion's name, for diffing two runs")
    void namesEveryFailure() {
        SuiteGrading.listEveryAssertion()
                .filter(assertion -> !SuiteGrading.holds(assertion))
                .map(SuiteFile.Assertion::toString)
                .sorted()
                .forEach(name -> System.out.println("FAIL " + name));
    }

    @Test
    @DisplayName("the assertions that answer false, named, group by group")
    void namesTheQuietDisagreements() {
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        for (SuiteFile.Assertion assertion : SuiteGrading.listEveryAssertion().toList()) {
            if (SuiteGrading.holds(assertion)
                    || !"answered false".equals(readTheReasonFor(assertion))) {
                continue;
            }
            byGroup.computeIfAbsent(assertion.file() + " / " + assertion.group(),
                    key -> new java.util.ArrayList<>()).add(assertion.source());
        }

        System.out.printf("%n%d groups answer false; the largest, in full:%n",
                byGroup.size());
        byGroup.entrySet().stream()
                .sorted((left, right) -> right.getValue().size() - left.getValue().size())
                .limit(8)
                .forEach(entry -> {
                    System.out.printf("%n%s (%d)%n", entry.getKey(), entry.getValue().size());
                    entry.getValue().forEach(source -> System.out.println(
                            "    " + source.replace("\n", " ")));
                });
    }

    @Test
    @DisplayName("the assertions that raised, named, so the reason can be checked")
    void namesTheRaisingAssertions() {
        List<SuiteFile.Assertion> raising = SuiteGrading.listEveryAssertion()
                .filter(assertion -> !SuiteGrading.holds(assertion))
                .filter(assertion -> readTheReasonFor(assertion).startsWith("error "))
                .toList();

        int shown = Math.min(60, raising.size());
        System.out.printf("%n%d assertions raised; showing %d:%n", raising.size(), shown);
        raising.stream().limit(shown).forEach(assertion -> System.out.printf(
                "  %-22s %s%n", readTheReasonFor(assertion),
                assertion.source().replace("\n", " ").substring(
                        0, Math.min(110, assertion.source().length()))));
        if (raising.size() > shown) {
            System.out.printf("  ... and %d more not shown%n", raising.size() - shown);
        }
    }

    @Test
    @DisplayName("what the failures are failing on, most common first")
    void countsTheFailures() {
        Map<String, Integer> byReason = new LinkedHashMap<>();
        int failures = 0;

        for (SuiteFile.Assertion assertion : SuiteGrading.listEveryAssertion().toList()) {
            if (SuiteGrading.holds(assertion)) {
                continue;
            }
            failures++;
            byReason.merge(readTheReasonFor(assertion), 1, Integer::sum);
        }

        System.out.printf("%n%d failing assertions, by what they trip over:%n", failures);
        byReason.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(40)
                .forEach(entry -> System.out.printf("  %5d  %s%n",
                        entry.getValue(), entry.getKey()));

        assertThat(SuiteGrading.listEveryAssertion().toList())
                .as("nothing to report must mean everything held, not that nothing ran")
                .isNotEmpty();
    }
}
