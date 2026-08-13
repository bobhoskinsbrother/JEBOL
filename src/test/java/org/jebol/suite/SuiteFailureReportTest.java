package org.jebol.suite;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the failing assertions are failing on, grouped.
 *
 * <p>Three and a half thousand failures is not a work list. This counts
 * them by the word they trip over, so the next thing to build is the top
 * line rather than whatever was noticed last.
 *
 * <p>Reports rather than asserts, for the same reason the coverage backlog
 * does: a test that stays red for weeks stops being read.
 */
class SuiteFailureReportTest {

    /**
     * The short reason an assertion did not hold, as recorded when it ran.
     *
     * <p>This used to run the assertion again in a fresh interpreter to
     * find out. A test file is a script, so an assertion run on its own
     * has lost whatever the lines above it set up, and the reason that
     * came back was about the missing setup rather than about the gap.
     * That put words called a, s, b and v at the top of the work list,
     * roughly three hundred and thirty entries of pure noise, and every
     * one of them was already passing or failing for some other reason.
     */
    private static String reasonFor(SuiteFile.Assertion assertion) {
        return RebolSuiteTest.verdictFor(assertion).reason();
    }

    @Test
    @DisplayName("the assertions that run and answer false, by group")
    void theQuietDisagreementsAreGrouped() {
        Map<String, Integer> byGroup = new LinkedHashMap<>();
        Map<String, String> anExample = new LinkedHashMap<>();

        for (SuiteFile.Assertion assertion : RebolSuiteTest.everyAssertion().toList()) {
            if (RebolSuiteTest.holds(assertion) || !"answered false".equals(reasonFor(assertion))) {
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
    void theLargestGroupsAreSampled() {
        Map<String, List<String>> examples = new LinkedHashMap<>();
        for (SuiteFile.Assertion assertion : RebolSuiteTest.everyAssertion().toList()) {
            if (RebolSuiteTest.holds(assertion)) {
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
    void stepsAreCounted() {
        RebolSuiteTest.filesInSuite().stream().limit(6).forEach(file ->
                System.out.printf("  %-24s %4d steps, %4d assertions, %4d setup%n",
                        file.name(), file.steps().size(), file.assertions().size(),
                        file.steps().stream().filter(step -> !step.isAssertion()).count()));
    }

    @Test
    @DisplayName("every assertion that escaped as a Java exception, named")
    void theHostExceptionsAreNamed() {
        // These are in a class of their own. Every other line in the work
        // list is something JEBOL has not got yet; this is something JEBOL
        // has and got wrong, and spec/embed.allium promises it cannot
        // happen -- a script failure must arrive as an outcome so a host
        // can tell it apart from a bug in the interpreter. Counting them
        // was not enough, because a count cannot be fixed.
        List<SuiteFile.Assertion> escaping = RebolSuiteTest.everyAssertion()
                .filter(assertion -> !RebolSuiteTest.holds(assertion))
                .filter(assertion -> reasonFor(assertion).startsWith("host exception"))
                .toList();

        System.out.printf("%n%d assertions escaped as a host exception:%n", escaping.size());
        escaping.forEach(assertion -> System.out.printf("  %-34s %s%n      %s%n",
                reasonFor(assertion),
                assertion.file() + " / " + assertion.group(),
                assertion.source().replace("\n", " ")));
    }

    @Test
    @DisplayName("every failing assertion's name, for diffing two runs")
    void everyFailureNamed() {
        // A plain list, so two runs can be compared. Which assertions a
        // change costs is not answerable from counts, and "it was passing
        // for the wrong reason" is a claim that needs checking.
        RebolSuiteTest.everyAssertion()
                .filter(assertion -> !RebolSuiteTest.holds(assertion))
                .map(SuiteFile.Assertion::toString)
                .sorted()
                .forEach(name -> System.out.println("FAIL " + name));
    }

    @Test
    @DisplayName("the assertions that answer false, named, group by group")
    void theQuietDisagreementsAreNamed() {
        // The largest bucket by far, and the only one a count says nothing
        // about: "answered false" is the same reason for every one of them.
        // Each has to be traced to the C on its own, and that needs the
        // source. Grouped so a whole area can be worked through at once.
        Map<String, List<String>> byGroup = new LinkedHashMap<>();
        for (SuiteFile.Assertion assertion : RebolSuiteTest.everyAssertion().toList()) {
            if (RebolSuiteTest.holds(assertion)
                    || !"answered false".equals(reasonFor(assertion))) {
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
    void theRaisingAssertionsAreNamed() {
        // A count of "error expect-arg" says nothing about whether JEBOL
        // was right to raise at all. Each of these has to be put to the
        // binary one at a time, and that needs the source rather than a
        // tally. Capped and the cap reported, so a truncated list cannot
        // read as the whole of it.
        List<SuiteFile.Assertion> raising = RebolSuiteTest.everyAssertion()
                .filter(assertion -> !RebolSuiteTest.holds(assertion))
                .filter(assertion -> reasonFor(assertion).startsWith("error "))
                .toList();

        int shown = Math.min(60, raising.size());
        System.out.printf("%n%d assertions raised; showing %d:%n", raising.size(), shown);
        raising.stream().limit(shown).forEach(assertion -> System.out.printf(
                "  %-22s %s%n", reasonFor(assertion),
                assertion.source().replace("\n", " ").substring(
                        0, Math.min(110, assertion.source().length()))));
        if (raising.size() > shown) {
            System.out.printf("  ... and %d more not shown%n", raising.size() - shown);
        }
    }

    @Test
    @DisplayName("what the failures are failing on, most common first")
    void theFailuresAreCounted() {
        Map<String, Integer> byReason = new LinkedHashMap<>();
        int failures = 0;

        for (SuiteFile.Assertion assertion : RebolSuiteTest.everyAssertion().toList()) {
            if (RebolSuiteTest.holds(assertion)) {
                continue;
            }
            failures++;
            byReason.merge(reasonFor(assertion), 1, Integer::sum);
        }

        System.out.printf("%n%d failing assertions, by what they trip over:%n", failures);
        byReason.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(40)
                .forEach(entry -> System.out.printf("  %5d  %s%n",
                        entry.getValue(), entry.getKey()));

        // An empty report used to mean the runner found nothing, and the
        // guard asserted non-empty to catch that. The suite has since gone
        // green, so an empty report is now the wanted answer and the guard
        // asks its real question instead: did the assertions run at all?
        assertThat(RebolSuiteTest.everyAssertion().toList())
                .as("nothing to report must mean everything held, not that nothing ran")
                .isNotEmpty();
    }
}
