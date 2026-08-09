package org.jebol.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.Molder;
import org.jebol.domain.value.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Anything MOLD prints, the reader reads back as an equal value.
 *
 * <p>This is the invariant that keeps code-as-data honest, and it is checked
 * against fourteen real programs rather than against examples chosen to make
 * it pass.
 */
class MoldRoundTripTest {

    static Stream<Path> programmes() {
        return CorpusReader.sourceProgrammes().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programmes")
    @DisplayName("mold then read gives back an equal block")
    void moldingThenReadingIsIdentity(Path programme) {
        BlockValue original = readOrFail(CorpusReader.read(programme));

        String molded = Molder.moldOnly(original);
        BlockValue reread = readOrFail(molded);

        assertThat(reread.remaining())
                .as("%s did not survive a round trip", programme.getFileName())
                .isEqualTo(original.remaining());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programmes")
    @DisplayName("and the round trip is stable, not merely once")
    void moldingIsStable(Path programme) {
        BlockValue original = readOrFail(CorpusReader.read(programme));

        String once = Molder.moldOnly(original);
        String twice = Molder.moldOnly(readOrFail(once));

        assertThat(twice)
                .as("%s molds differently the second time", programme.getFileName())
                .isEqualTo(once);
    }

    @Nested
    @DisplayName("individual forms survive the trip")
    class IndividualForms {

        @Test
        void everyLiteralFormInTheLoadingCorpus() {
            List<CorpusEntry> loaders = CorpusReader.allEntries().stream()
                    .filter(entry -> entry.expectedTypes().isPresent())
                    .toList();

            assertThat(loaders).isNotEmpty();
            loaders.forEach(entry -> {
                BlockValue original = readOrFail(entry.code());
                BlockValue reread = readOrFail(Molder.moldOnly(original));

                assertThat(reread.remaining())
                        .as("%s -- %s", entry.id(), entry.origin())
                        .isEqualTo(original.remaining());
            });
        }

        @Test
        @DisplayName("a string keeps its quotes and escapes")
        void stringsSurvive() {
            assertRoundTrips("\"line^/tab^-quote^\"caret^^\"");
        }

        @Test
        @DisplayName("an empty block is not lost")
        void emptyBlockSurvives() {
            assertRoundTrips("[]");
        }

        @Test
        @DisplayName("nesting is preserved to the bottom")
        void nestingSurvives() {
            assertRoundTrips("[a [b [c [d]]]]");
        }

        @Test
        @DisplayName("the awkward numeric forms keep their datatypes")
        void numericFormsSurvive() {
            assertRoundTrips("1.2 1.2.3 1.2.3.4.5 -1 40x40 .5 10:30");
        }

        @Test
        @DisplayName("word shapes keep their shape")
        void wordShapesSurvive() {
            assertRoundTrips("word set-word: :get-word 'lit-word /refinement #issue");
        }

        @Test
        @DisplayName("paths keep their shape, including the awkward one")
        void pathShapesSurvive() {
            assertRoundTrips("a/b window/pane/:n/color: :obj/field 'quoted/path");
        }

        private void assertRoundTrips(String source) {
            BlockValue original = readOrFail(source);
            BlockValue reread = readOrFail(Molder.moldOnly(original));

            assertThat(reread.remaining())
                    .as("source: %s%nmolded: %s", source, Molder.moldOnly(original))
                    .isEqualTo(original.remaining());
        }
    }

    private static BlockValue readOrFail(String source) {
        TranscodeResult result = Transcoder.transcode(source);
        assertThat(result.succeeded())
                .as("could not read: %s%n  source: %s",
                        result.error().map(Value::toString).orElse("(no detail)"),
                        source.length() > 200 ? source.substring(0, 200) + "..." : source)
                .isTrue();
        return result.values().orElseThrow();
    }
}
