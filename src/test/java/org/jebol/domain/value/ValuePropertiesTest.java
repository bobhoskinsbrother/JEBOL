package org.jebol.domain.value;

import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The laws from {@code spec/values.allium} and {@code spec/load.allium} as
 * properties over generated values, rather than as examples.
 *
 * <p>An invariant asserted against three hand-picked cases is three examples
 * wearing a law's clothes. These generate the cases instead, which is how the
 * awkward ones get found.
 */
class ValuePropertiesTest {

    @Property
    void everyLegalPositionIsAcceptedAndNoOtherIs(
            @ForAll @Size(max = 12) List<@IntRange(min = -50, max = 50) Integer> contents,
            @ForAll @IntRange(min = -5, max = 25) int position) {

        BlockValue block = BlockValue.block(
                contents.stream().map(number -> (Value) IntegerValue.of(number)).toList());
        boolean legal = position >= 1 && position <= contents.size() + 1;

        if (legal) {
            assertThat(block.atIndex(position).index()).isEqualTo(position);
        } else {
            assertThat(catchIllegalArgument(() -> block.atIndex(position)))
                    .as("position %d in a block of %d must be refused",
                            position, contents.size())
                    .isTrue();
        }
    }

    @Property
    void lengthFromHerePlusPositionAlwaysReachesTheTail(
            @ForAll @Size(max = 12) List<@IntRange(min = 0, max = 9) Integer> contents,
            @ForAll @IntRange(min = 1, max = 13) int rawPosition) {

        BlockValue block = BlockValue.block(
                contents.stream().map(number -> (Value) IntegerValue.of(number)).toList());
        int position = Math.min(rawPosition, contents.size() + 1);
        BlockValue positioned = block.atIndex(position);

        assertThat(positioned.index() + positioned.lengthFromHere())
                .isEqualTo(positioned.storageLength() + 1);
    }

    @Property
    void headAndTailAreAlwaysLegalPositions(
            @ForAll @Size(max = 20) List<@IntRange(min = 0, max = 9) Integer> contents) {
        BlockValue block = BlockValue.block(
                contents.stream().map(number -> (Value) IntegerValue.of(number)).toList());

        assertThat(block.head().atHead()).isTrue();
        assertThat(block.tail().atTail()).isTrue();
        assertThat(block.head().storage()).isSameAs(block.tail().storage());
    }

    @Property
    void repositioningNeverCopiesTheStorage(
            @ForAll @Size(min = 1, max = 12) List<@IntRange(min = 0, max = 9) Integer> contents,
            @ForAll @IntRange(min = 1, max = 12) int rawPosition) {

        BlockValue block = BlockValue.block(
                contents.stream().map(number -> (Value) IntegerValue.of(number)).toList());
        int position = Math.min(rawPosition, contents.size() + 1);

        assertThat(block.atIndex(position).sharesStorageWith(block)).isTrue();
    }

    @Property
    void aWordAlwaysComparesByItsLowercasedSpelling(@ForAll("wordSpellings") String spelling) {
        WordValue word = WordValue.of(spelling);

        assertThat(word.canonical()).isEqualTo(spelling.toLowerCase(Locale.ROOT));
        assertThat(word.spelling()).as("case is preserved for printing").isEqualTo(spelling);
        assertThat(word.namesSameAs(WordValue.of(spelling.toUpperCase(Locale.ROOT))))
                .isTrue();
    }

    @Property
    void changingAWordsShapeNeverChangesWhatItNames(
            @ForAll("wordSpellings") String spelling) {
        WordValue plain = WordValue.of(spelling);

        for (Datatype shape : List.of(Datatype.SET_WORD, Datatype.GET_WORD,
                Datatype.LIT_WORD, Datatype.REFINEMENT, Datatype.ISSUE)) {
            assertThat(plain.as(shape).namesSameAs(plain))
                    .as("%s as %s", spelling, shape)
                    .isTrue();
        }
    }

    @Provide
    Arbitrary<String> wordSpellings() {
        Arbitrary<Character> first = Arbitraries.chars().alpha();
        Arbitrary<String> rest = Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .ofMaxLength(8);
        return Combinators.combine(first, rest).as((head, tail) -> head + tail);
    }

    @Property
    void moldingAnIntegerAlwaysReadsBackEqual(@ForAll long magnitude) {
        assertReadsBackEqual(IntegerValue.of(magnitude));
    }

    @Property
    void moldingAPairAlwaysReadsBackEqual(
            @ForAll @IntRange(min = -9999, max = 9999) int x,
            @ForAll @IntRange(min = -9999, max = 9999) int y) {
        assertReadsBackEqual(PairValue.of(x, y));
    }

    @Property
    void moldingATupleAlwaysReadsBackEqual(
            @ForAll @Size(min = 3, max = 12) List<@IntRange(min = 0, max = 255) Integer> segments) {
        assertReadsBackEqual(
                TupleValue.of(segments.stream().mapToInt(Integer::intValue).toArray()));
    }

    @Property
    void moldingAStringAlwaysReadsBackEqual(@ForAll @Size(max = 30) String text) {
        assertReadsBackEqual(StringValue.of(text));
    }

    @Property
    void moldingAWordAlwaysReadsBackEqual(@ForAll("wordSpellings") String spelling) {
        for (Datatype shape : List.of(Datatype.WORD, Datatype.SET_WORD,
                Datatype.GET_WORD, Datatype.LIT_WORD, Datatype.REFINEMENT)) {
            assertReadsBackEqual(WordValue.of(spelling, shape));
        }
    }

    @Property
    void moldingABlockOfIntegersAlwaysReadsBackEqual(
            @ForAll @Size(max = 10) List<@IntRange(min = -999, max = 999) Integer> contents) {
        assertReadsBackEqual(BlockValue.block(
                contents.stream().map(number -> (Value) IntegerValue.of(number)).toList()));
    }

    /**
     * The round trip that keeps code-as-data honest: whatever MOLD prints,
     * the reader reads back as an equal value.
     */
    private static void assertReadsBackEqual(Value original) {
        String molded = Molder.mold(original);
        TranscodeResult result = Transcoder.transcode(molded);

        assertThat(result.succeeded())
                .as("could not read back [%s], molded from %s",
                        molded, original.datatype().literalSpelling())
                .isTrue();

        List<Value> readBack = result.values().orElseThrow().remaining();
        assertThat(readBack)
                .as("molding %s gave [%s], which read back as %d values",
                        original.datatype().literalSpelling(), molded, readBack.size())
                .hasSize(1);
        assertThat(readBack.get(0))
                .as("molded as [%s]", molded)
                .isEqualTo(original);
    }

    private static boolean catchIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
