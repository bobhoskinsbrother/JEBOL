package org.jebol.corpus;

import org.jebol.testing.RunsOn;
import org.jebol.testing.TestTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


@RunsOn(value = TestTarget.DESKTOP_LINUX, because = "it reads corpus/, which only the repository has")
class CorpusCoverageReportTest {

    @Test
    @DisplayName("report every entry that asserts behaviour")
    void reportCoverage() {
        List<CorpusEntry> assertable = CorpusReader.readAllEntries().stream()
                .filter(CorpusEntry::assertsSomething)
                .filter(entry -> entry.expectedTypes().isEmpty())
                .toList();

        List<String> passing = new ArrayList<>();
        Map<String, String> failing = new LinkedHashMap<>();

        for (CorpusEntry entry : assertable) {
            try {
                CorpusRunner.Result result = CorpusRunner.run(entry);
                result.describeTheMismatch(entry).ifPresentOrElse(
                        reason -> failing.put(entry.id(), reason),
                        () -> passing.add(entry.id()));
            } catch (RuntimeException unexpected) {
                failing.put(entry.id(),
                        unexpected.getClass().getSimpleName() + ": " + unexpected.getMessage());
            }
        }

        System.out.printf("%n=== corpus coverage ===%n%d of %d entries pass%n%n",
                passing.size(), assertable.size());
        failing.forEach((id, reason) -> System.out.printf("  FAIL %-48s %s%n", id, reason));
        System.out.println();

        assertThat(passing.size())
                .as("corpus entries passing; this floor stops it going backwards")
                .isGreaterThanOrEqualTo(262);
    }
}
