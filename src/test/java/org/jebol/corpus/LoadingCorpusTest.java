package org.jebol.corpus;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Value;
import org.jebol.domain.value.WordValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end over the reader, driven by real REBOL rather than by examples
 * invented to suit the implementation.
 *
 * <p>Every entry is a line lifted from the fourteen programs in
 * {@code corpus/sources}, cited by file and line. Real code uses the awkward
 * combinations; a hand-written example tends not to.
 */
class LoadingCorpusTest {

    static Stream<CorpusEntry> entriesWithTypeAssertions() {
        return CorpusReader.allEntries().stream()
                .filter(entry -> entry.expectedTypes().isPresent());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entriesWithTypeAssertions")
    @DisplayName("real source lines load to the datatypes they should")
    void loadsToTheExpectedDatatypes(CorpusEntry entry) {
        TranscodeResult result = Transcoder.transcode(entry.code());

        assertThat(result.succeeded())
                .as("%s (%s) failed to read: %s",
                        entry.id(), entry.origin(),
                        result.error().map(Object::toString).orElse(""))
                .isTrue();

        List<String> actual = result.values().orElseThrow().remaining().stream()
                .map(value -> value.datatype().spelling())
                .toList();

        assertThat(actual)
                .as("%s -- %s%n  code: %s",
                        entry.id(), entry.origin(), entry.code().replace("\n", "\\n"))
                .isEqualTo(entry.expectedTypes().orElseThrow());
    }

    @Test
    @DisplayName("the loading corpus is actually being exercised")
    void loadingCorpusIsNotEmpty() {
        assertThat(entriesWithTypeAssertions().toList())
                .as("corpus entries carrying a types assertion")
                .hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("reading is a function of the text alone")
    void readingIsDeterministic() {
        CorpusReader.allEntries().stream()
                .filter(entry -> entry.expectedTypes().isPresent())
                .forEach(entry -> {
                    TranscodeResult first = Transcoder.transcode(entry.code());
                    TranscodeResult second = Transcoder.transcode(entry.code());

                    assertThat(first.succeeded())
                            .as("%s read differently on a second attempt", entry.id())
                            .isEqualTo(second.succeeded());
                    assertThat(first.values().map(BlockValue::remaining))
                            .as("%s produced different values on a second attempt", entry.id())
                            .isEqualTo(second.values().map(BlockValue::remaining));
                });
    }

    @Test
    @DisplayName("nothing the reader produces carries a binding")
    void everyWordComesBackUnbound() {
        CorpusReader.allEntries().stream()
                .filter(entry -> entry.expectedTypes().isPresent())
                .map(entry -> Transcoder.transcode(entry.code()))
                .filter(TranscodeResult::succeeded)
                .forEach(result -> assertThat(unboundnessOf(result.values().orElseThrow()))
                        .as("transcode must produce unbound words")
                        .isTrue());
    }

    private static boolean unboundnessOf(Value value) {
        return switch (value) {
            case WordValue word -> !word.isBound();
            case BlockValue block -> block.remaining().stream()
                    .allMatch(LoadingCorpusTest::unboundnessOf);
            default -> true;
        };
    }
}
