package org.jebol.domain.value;

import org.jebol.domain.eval.Binder;
import org.jebol.domain.read.TranscodeResult;
import org.jebol.domain.read.Transcoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoundTripInvariantTest {

    private static AnyBlockValue read(String source) {
        TranscodeResult result = Transcoder.transcode(source);
        assertThat(result.succeeded()).as("could not read: %s", source).isTrue();
        return result.values().orElseThrow();
    }

    private static void assertRoundTrips(AnyBlockValue original, String description) {
        AnyBlockValue reread = read(Molder.moldOnly(original));
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
            context.register("known", IntegerValue.of(1));

            AnyBlockValue bound = Binder.bind(read("known unknown [known]"), context);

            assertRoundTrips(bound, "a block bound to a context");
        }

        @Test
        @DisplayName("because equality asks what a word says, not which word it is")
        void equalityIgnoresBinding() {
            Context first = Context.root();
            first.register("shared", IntegerValue.of(1));
            Context second = Context.root();
            second.register("shared", IntegerValue.of(2));

            AnyWordValue unbound = WordValue.of("shared");
            AnyWordValue inFirst = unbound.boundTo(first);
            AnyWordValue inSecond = unbound.boundTo(second);

            assertThat(inFirst).as("equal? ignores binding").isEqualTo(inSecond);
            assertThat(inFirst).as("and ignores whether there is one at all").isEqualTo(unbound);
        }

        @Test
        @DisplayName("and same? is the question that does count binding")
        void samenessCountsBinding() {
            Context context = Context.root();
            context.register("shared", IntegerValue.of(1));

            AnyWordValue unbound = WordValue.of("shared");
            AnyWordValue bound = unbound.boundTo(context);

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
                    .isNotEqualTo(SetWordValue.of("total"));
        }
    }

    @Nested
    @DisplayName("values positioned away from their head")
    class RepositionedValues {

        @Test
        @DisplayName("a block read from partway through round-trips")
        void repositionedBlockSurvives() {
            AnyBlockValue whole = read("a b c d");
            assertRoundTrips(whole.atIndex(3), "a block positioned at its third item");
        }

        @Test
        void repositionedStringSurvives() {
            AnyStringValue whole = StringValue.of("hello world");
            AnyBlockValue holding = BlockValue.block(whole.atIndex(7));

            assertRoundTrips(holding, "a string positioned partway through");
        }
    }

    @Nested
    @DisplayName("values that were mutated after being read")
    class MutatedValues {

        @Test
        void appendedBlockSurvives() {
            AnyBlockValue block = read("[a b]");
            AnyBlockValue inner = (AnyBlockValue) block.remaining().get(0);
            inner.storage().append(WordValue.of("c"));

            assertRoundTrips(block, "a block appended to after reading");
        }

        @Test
        void mutatedStringSurvives() {
            AnyBlockValue block = read("\"ab\"");
            AnyStringValue text = (AnyStringValue) block.remaining().get(0);
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
