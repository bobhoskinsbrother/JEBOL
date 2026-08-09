package org.jebol.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The corpus is test data, so it needs testing too. A malformed entry that
 * silently asserts nothing would look like a passing test for ever.
 */
class CorpusIntegrityTest {

    private static final List<CorpusEntry> ENTRIES = CorpusReader.allEntries();

    @Test
    @DisplayName("the corpus files were found and parsed")
    void corpusIsNotEmpty() {
        assertThat(ENTRIES).as("entries parsed from corpus/*.corpus").isNotEmpty();
    }

    @Test
    @DisplayName("every entry asserts something")
    void everyEntryAssertsSomething() {
        assertThat(ENTRIES)
                .allSatisfy(entry -> assertThat(entry.assertsSomething())
                        .as("%s asserts nothing: it has no result, prints, error or types",
                                entry.id())
                        .isTrue());
    }

    @Test
    @DisplayName("no two entries share an id")
    void identifiersAreUnique() {
        assertThat(ENTRIES.stream().map(CorpusEntry::id).toList()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every entry records where it came from")
    void everyEntryHasAnOrigin() {
        assertThat(ENTRIES)
                .allSatisfy(entry -> assertThat(entry.origin())
                        .as("%s has no origin", entry.id())
                        .isNotBlank());
    }

    @Test
    @DisplayName("capability tags are from the documented vocabulary")
    void capabilityTagsAreKnown() {
        List<String> known = List.of(
                "op", "output", "series", "control", "object",
                "clock", "random", "file", "network", "parse", "r2-only");

        assertThat(ENTRIES)
                .allSatisfy(entry -> assertThat(known)
                        .as("%s requires %s, which is not in corpus/README.md",
                                entry.id(), entry.requires())
                        .containsAll(entry.requires()));
    }

    @Test
    @DisplayName("the fourteen source programmes are present")
    void sourceProgrammesArePresent() {
        List<Path> sources = CorpusReader.sourceProgrammes();

        assertThat(sources).hasSize(14);
        assertThat(sources)
                .allSatisfy(path -> assertThat(CorpusReader.read(path))
                        .as("%s is empty", path)
                        .isNotBlank());
    }

    @Test
    @DisplayName("almost every entry runs against what exists today")
    void nearlyEveryEntryIsRunnable() {
        List<CorpusEntry> blocked =
                ENTRIES.stream().filter(entry -> !entry.isRunnableNow()).toList();

        assertThat(ENTRIES.stream().filter(CorpusEntry::isRunnableNow).toList())
                .as("entries that can run today")
                .isNotEmpty();
        assertThat(blocked)
                .as("entries still waiting on something unbuilt: %s",
                        blocked.stream().map(CorpusEntry::id).toList())
                .hasSizeLessThan(5);
    }
}
