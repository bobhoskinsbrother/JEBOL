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
                .isInstanceOfSatisfying(AnyWordValue.class, word ->
                        assertThat(word.canonical()).isEqualTo("rebol"));
        assertThat(values.get(1))
                .as("%s should follow its header word with a block", programme.getFileName())
                .isInstanceOfSatisfying(AnyBlockValue.class, block ->
                        assertThat(block.datatype()).isEqualTo(BlockValue.TYPE));
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

        assertThat(countOf(everything, PairValue.TYPE)).as("pairs").isGreaterThan(150);
        assertThat(countOf(everything, TupleValue.TYPE)).as("tuples").isGreaterThan(70);
        assertThat(countOf(everything, SetWordValue.TYPE)).as("set-words").isGreaterThan(500);
        assertThat(countOf(everything, PathValue.TYPE)).as("paths").isGreaterThan(300);
        assertThat(countOf(everything, LitWordValue.TYPE)).as("lit-words").isGreaterThan(50);
        assertThat(countOf(everything, RefinementValue.TYPE)).as("refinements").isGreaterThan(10);
        assertThat(countOf(everything, GetWordValue.TYPE)).as("get-words").isGreaterThan(5);
        assertThat(countOf(everything, SetPathValue.TYPE)).as("set-paths").isGreaterThan(20);
        assertThat(countOf(everything, StringValue.TYPE)).as("strings").isGreaterThan(50);
        assertThat(countOf(everything, IntegerValue.TYPE)).as("integers").isGreaterThan(100);
    }

    private static long countOf(List<Value> values, Datatype datatype) {
        return values.stream().filter(value -> value.datatype() == datatype).count();
    }

    private static List<Value> flatten(AnyBlockValue block) {
        return block.remaining().stream()
                .flatMap(value -> value instanceof AnyBlockValue nested
                        ? Stream.concat(Stream.of(value), flatten(nested).stream())
                        : Stream.of(value))
                .toList();
    }

    private static List<String> boundWordsIn(Value value) {
        return switch (value) {
            case AnyWordValue word -> word.isBound() ? List.of(word.spelling()) : List.of();
            case AnyBlockValue block -> block.remaining().stream()
                    .flatMap(item -> boundWordsIn(item).stream())
                    .toList();
            default -> List.of();
        };
    }

    private static long seriesAwayFromHead(Value value) {
        long here = value instanceof RebolSeries series && !series.atHead() ? 1 : 0;
        if (value instanceof AnyBlockValue block) {
            return here + block.remaining().stream()
                    .mapToLong(SourceProgrammeLoadingTest::seriesAwayFromHead)
                    .sum();
        }
        return here;
    }
}
