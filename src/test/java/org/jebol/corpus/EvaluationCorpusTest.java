package org.jebol.corpus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationCorpusTest {

    static Stream<CorpusEntry> runnableEntries() {
        return CorpusReader.allEntries().stream()
                .filter(CorpusEntry::isRunnableNow)
                .filter(CorpusEntry::assertsSomething)
                .filter(entry -> entry.expectedTypes().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runnableEntries")
    @DisplayName("published examples give their published results")
    void producesThePublishedResult(CorpusEntry entry) {
        CorpusRunner.Result result = CorpusRunner.run(entry);

        assertThat(result.mismatch(entry))
                .as("%s -- %s%n  code: %s", entry.id(), entry.origin(), entry.code())
                .isEmpty();
    }

    @Test
    @DisplayName("the runnable set is not empty, so this test is doing something")
    void thereAreRunnableEntries() {
        assertThat(runnableEntries().toList()).isNotEmpty();
    }
}
