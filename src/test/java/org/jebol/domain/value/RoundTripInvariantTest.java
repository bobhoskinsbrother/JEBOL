package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jebol.domain.eval.Binder;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Every value survives a round trip through MOLD.
 *
 * <p>The earlier round-trip tests only ever molded values fresh from the
 * reader, which are unbound, so they never noticed that a bound block did not
 * survive. Binding is not syntax and MOLD does not print it, so a round trip
 * cannot preserve it; what it must preserve is the value, and in REBOL two
 * words are equal when they are spelled alike regardless of binding.
 */
class RoundTripInvariantTest {

    private static BlockValue read(String source) {
        TranscodeResult result = Transcoder.transcode(source);
        assertThat(result.succeeded()).as("could not read: %s", source).isTrue();
        return result.values().orElseThrow();
    }

    private static void assertRoundTrips(BlockValue original, String description) {
        BlockValue reread = read(Molder.moldOnly(original));
        assertThat(reread.remaining())
                .as("%s did not survive: molded as [%s]",
                        description, Molder.moldOnly(original))
                .isEqualTo(original.remaining());
    }

    @Nested
    @DisplayName("bound values, which is where this was quietly failing")
    class BoundValues {

        @Test
        @DisplayName("a bound block round-trips")
        void boundBlockSurvives() {
            Context context = Context.root();
            context.set("known", IntegerValue.of(1));

            BlockValue bound = Binder.bind(read("known unknown [known]"), context);

            assertRoundTrips(bound, "a block bound to a context");
        }

        @Test
        @DisplayName("because equality asks what a word says, not which word it is")
        void equalityIgnoresBinding() {
            Context first = Context.root();
            first.set("shared", IntegerValue.of(1));
            Context second = Context.root();
            second.set("shared", IntegerValue.of(2));

            WordValue unbound = WordValue.of("shared");
            WordValue inFirst = unbound.boundTo(first);
            WordValue inSecond = unbound.boundTo(second);

            assertThat(inFirst).as("equal? ignores binding").isEqualTo(inSecond);
            assertThat(inFirst).as("and ignores whether there is one at all").isEqualTo(unbound);
        }

        @Test
        @DisplayName("and same? is the question that does count binding")
        void samenessCountsBinding() {
            Context context = Context.root();
            context.set("shared", IntegerValue.of(1));

            WordValue unbound = WordValue.of("shared");
            WordValue bound = unbound.boundTo(context);

            assertThat(bound.isSameAs(unbound)).isFalse();
            assertThat(bound.isSameAs(unbound.boundTo(context))).isTrue();
        }

        @Test
        @DisplayName("== stays case sensitive even though binding is ignored")
        void strictEqualityIsStillCaseSensitive() {
            assertThat(WordValue.of("Print")).isNotEqualTo(WordValue.of("print"));
            assertThat(WordValue.of("Print").namesSameAs(WordValue.of("print"))).isTrue();
        }

        @Test
        @DisplayName("and a word is still not a set-word")
        void shapeStillCounts() {
            assertThat((Value) WordValue.of("total"))
                    .isNotEqualTo(WordValue.of("total", Datatype.SET_WORD));
        }
    }

    @Nested
    @DisplayName("values positioned away from their head")
    class RepositionedValues {

        @Test
        @DisplayName("a block read from partway through round-trips")
        void repositionedBlockSurvives() {
            BlockValue whole = read("a b c d");
            assertRoundTrips(whole.atIndex(3), "a block positioned at its third item");
        }

        @Test
        void repositionedStringSurvives() {
            StringValue whole = StringValue.of("hello world");
            BlockValue holding = BlockValue.block(whole.atIndex(7));

            assertRoundTrips(holding, "a string positioned partway through");
        }
    }

    @Nested
    @DisplayName("values that were mutated after being read")
    class MutatedValues {

        @Test
        void appendedBlockSurvives() {
            BlockValue block = read("[a b]");
            BlockValue inner = (BlockValue) block.remaining().get(0);
            inner.storage().append(WordValue.of("c"));

            assertRoundTrips(block, "a block appended to after reading");
        }

        @Test
        void mutatedStringSurvives() {
            BlockValue block = read("\"ab\"");
            StringValue text = (StringValue) block.remaining().get(0);
            text.storage().append('c');

            assertRoundTrips(block, "a string appended to after reading");
        }
    }

    @Nested
    @DisplayName("every datatype the reader can produce")
    class EveryReadableDatatype {

        @Test
        @DisplayName("one of each, in a single block")
        void oneOfEachSurvives() {
            String everything = String.join(" ", List.of(
                    "none", "true", "false",
                    "42", "-7", "1.5", ".5", "3.0e8",
                    "$12.50", "#\"a\"", "40x40", "1.2.3", "10:30", "15-May-2000",
                    "\"text\"", "%file.r", "http://example.com", "user@example.com",
                    "<tag>", "#{DEADBEEF}",
                    "word", "set-word:", ":get-word", "'lit-word", "/refinement", "#issue",
                    "[block]", "(paren)", "a/b", "a/b:", ":a/b", "'a/b",
                    "integer!", "string!"));

            assertRoundTrips(read(everything), "one value of every readable datatype");
        }

        @Test
        @DisplayName("and the empty forms, which are easy to lose")
        void emptyFormsSurvive() {
            assertRoundTrips(read("[] () \"\" #{}"), "the empty forms");
        }
    }
}
