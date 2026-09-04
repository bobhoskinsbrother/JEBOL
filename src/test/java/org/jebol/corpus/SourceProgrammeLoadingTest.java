package org.jebol.corpus;

import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end test for reading: fourteen complete REBOL programs, written
 * by other people for a different implementation, loaded whole.
 *
 * <p>None of them can be run without a graphics stack. All of them must load,
 * and between them they carry 184 pairs, 90 tuples, 771 set-words, 478 paths,
 * 86 lit-words, 28 refinements and 21 get-words. No hand-written example
 * would have thought to include what these do.
 */
class SourceProgrammeLoadingTest {

    static Stream<Path> programmes() {
        return CorpusReader.sourceProgrammes().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programmes")
    @DisplayName("loads without a syntax error")
    void loadsWithoutError(Path programme) {
        TranscodeResult result = Transcoder.transcode(CorpusReader.read(programme));

        assertThat(result.succeeded())
                .as("%s failed to read: %s", programme.getFileName(),
                        result.error().map(Object::toString).orElse("(no detail)"))
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programmes")
    @DisplayName("starts with a REBOL header, read as ordinary values")
    void startsWithAHeader(Path programme) {
        TranscodeResult result = Transcoder.transcode(CorpusReader.read(programme));
        List<Value> values = result.values().orElseThrow().remaining();

        assertThat(values).as("%s produced no values", programme.getFileName()).isNotEmpty();
        assertThat(values.get(0))
                .as("%s should begin with the word REBOL", programme.getFileName())
                .isInstanceOfSatisfying(WordValue.class, word ->
                        assertThat(word.canonical()).isEqualTo("rebol"));
        assertThat(values.get(1))
                .as("%s should follow its header word with a block", programme.getFileName())
                .isInstanceOfSatisfying(BlockValue.class, block ->
                        assertThat(block.datatype()).isEqualTo(Datatype.BLOCK));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programmes")
    @DisplayName("every word comes back unbound")
    void everyWordIsUnbound(Path programme) {
        TranscodeResult result = Transcoder.transcode(CorpusReader.read(programme));

        assertThat(boundWordsIn(result.values().orElseThrow()))
                .as("%s: transcode must not bind anything", programme.getFileName())
                .isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programmes")
    @DisplayName("every series sits at its head")
    void everySeriesIsAtItsHead(Path programme) {
        TranscodeResult result = Transcoder.transcode(CorpusReader.read(programme));

        assertThat(seriesAwayFromHead(result.values().orElseThrow()))
                .as("%s: nothing in the syntax can express a series positioned elsewhere",
                        programme.getFileName())
                .isZero();
    }

    @Test
    @DisplayName("between them they cover the forms that matter")
    void theProgrammesCoverTheAwkwardForms() {
        List<Value> everything = programmes()
                .map(CorpusReader::read)
                .map(Transcoder::transcode)
                .filter(TranscodeResult::succeeded)
                .flatMap(result -> flatten(result.values().orElseThrow()).stream())
                .toList();

        assertThat(countOf(everything, Datatype.PAIR)).as("pairs").isGreaterThan(150);
        assertThat(countOf(everything, Datatype.TUPLE)).as("tuples").isGreaterThan(70);
        assertThat(countOf(everything, Datatype.SET_WORD)).as("set-words").isGreaterThan(500);
        assertThat(countOf(everything, Datatype.PATH)).as("paths").isGreaterThan(300);
        assertThat(countOf(everything, Datatype.LIT_WORD)).as("lit-words").isGreaterThan(50);
        assertThat(countOf(everything, Datatype.REFINEMENT)).as("refinements").isGreaterThan(10);
        assertThat(countOf(everything, Datatype.GET_WORD)).as("get-words").isGreaterThan(5);
        assertThat(countOf(everything, Datatype.SET_PATH)).as("set-paths").isGreaterThan(20);
        assertThat(countOf(everything, Datatype.STRING)).as("strings").isGreaterThan(50);
        assertThat(countOf(everything, Datatype.INTEGER)).as("integers").isGreaterThan(100);
    }

    private static long countOf(List<Value> values, Datatype datatype) {
        return values.stream().filter(value -> value.datatype() == datatype).count();
    }

    private static List<Value> flatten(BlockValue block) {
        return block.remaining().stream()
                .flatMap(value -> value instanceof BlockValue nested
                        ? Stream.concat(Stream.of(value), flatten(nested).stream())
                        : Stream.of(value))
                .toList();
    }

    private static List<String> boundWordsIn(Value value) {
        return switch (value) {
            case WordValue word -> word.isBound() ? List.of(word.spelling()) : List.of();
            case BlockValue block -> block.remaining().stream()
                    .flatMap(item -> boundWordsIn(item).stream())
                    .toList();
            default -> List.of();
        };
    }

    private static long seriesAwayFromHead(Value value) {
        long here = value instanceof SeriesValue series && !series.atHead() ? 1 : 0;
        if (value instanceof BlockValue block) {
            return here + block.remaining().stream()
                    .mapToLong(SourceProgrammeLoadingTest::seriesAwayFromHead)
                    .sum();
        }
        return here;
    }
}
