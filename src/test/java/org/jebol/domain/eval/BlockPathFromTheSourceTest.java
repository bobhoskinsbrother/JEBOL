package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Reaching into a block through a path, read out of {@code PD_Block} in
 * {@code t-block.c} and checked against the R3 binary.
 *
 * <p>The thing to know first: a word selector does not answer the word, it
 * answers the item <em>after</em> the word. {@code [a 1 b 2]/a} is 1. That is
 * what makes a plain block a lookup table, and Rebol's own code reads settings
 * out of blocks that way constantly -- a path that refused it stopped
 * {@code prot-mysql.reb} and Rebol's own URL parser.
 *
 * <p>Two more that are not guessable. There is no position zero, and a negative
 * position counts back from where the block is and may reach behind it. And a
 * selector that finds nothing answers none for a read but refuses a write, which
 * is the only place the two part company: the C keeps the open question written
 * beside the function -- "a/not-found: 10 error or append?" -- and answers it by
 * refusing.
 *
 * <p>Specified in {@code spec/natives.allium} under "Reaching into a block
 * through a path".
 */
class BlockPathFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a number selects a position")
    class ByPosition {

        @Test
        @DisplayName("counting from one, from where the block is")
        void countingFromOne() {
            assertThat(answerTo("b: [a 1 c 2] b/1")).isEqualTo("a");
            assertThat(answerTo("b: [a 1 c 2] b/2")).isEqualTo("1");
            assertThat(answerTo("b: next [a 1 c 2] b/1")).isEqualTo("1");
        }

        @Test
        @DisplayName("and there is no position zero")
        void thereIsNoPositionZero() {
            // `if (i == 0) return PE_NONE; // like in case: path/0`
            assertThat(answerTo("b: [a 1] none? b/0")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a negative position counts back, and may reach behind the block")
        void aNegativePositionCountsBack() {
            // `if (i < 0) i++; n = i + VAL_INDEX(pvs->value) - 1;` -- so -1 is
            // the item before the position rather than the last item, and a
            // block at its head has nothing there.
            assertThat(answerTo("b: next next [a 1 c 2] b/-1")).isEqualTo("1");
            assertThat(answerTo("b: [a 1 c 2] none? b/-1")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a decimal is truncated rather than refused")
        void aDecimalIsTruncated() {
            assertThat(answerTo("b: [a 1 c 2] b/2.7")).isEqualTo("1");
        }

        @Test
        @DisplayName("and a position past the tail answers none")
        void pastTheTailIsNone() {
            assertThat(answerTo("b: [a 1] none? b/9")).isEqualTo(TRUE);
            assertThat(answerTo("b: [] none? b/1")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a word selects the item after it")
    class ByName {

        @Test
        @DisplayName("so a block is a lookup table")
        void aBlockIsALookupTable() {
            // `n = Find_Word(...); if (n != NOT_FOUND) n++;`
            assertThat(answerTo("b: [a 1 c 2] b/a")).isEqualTo("1");
            assertThat(answerTo("b: [a 1 c 2] b/c")).isEqualTo("2");
        }

        @Test
        @DisplayName("and how the word in the block was written makes no difference")
        void anyKindOfWordMatches() {
            // Find_Word takes "word (of any type)" and compares the spelling,
            // so a block written with colons reads the same as one without.
            assertThat(answerTo("b: [a: 1] b/a")).isEqualTo("1");
            assertThat(answerTo("b: reduce ['a 1] b/a")).isEqualTo("1");
        }

        @Test
        @DisplayName("the first match wins, and the search starts where the block is")
        void theFirstMatchFromHere() {
            assertThat(answerTo("b: [a 1 a 2] b/a")).isEqualTo("1");
            assertThat(answerTo("b: next next [a 1 a 2] b/a")).isEqualTo("2");
        }

        @Test
        @DisplayName("a name the block has not got answers none")
        void anUnknownNameIsNone() {
            assertThat(answerTo("b: [a 1] none? b/zz")).isEqualTo(TRUE);
            assertThat(answerTo("b: [] none? b/a")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a name at the tail with nothing after it answers none too")
        void aNameWithNothingAfterItIsNone() {
            // One line covers both: `if (n < 0 || (REBCNT)n >= VAL_TAIL(...))`.
            // The name is there and the answer is not, which is the same
            // outcome as the name not being there at all.
            assertThat(answerTo("b: [a] none? b/a")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a path may go deeper, because what it finds may be a block")
        void itGoesDeeper() {
            assertThat(answerTo("b: [a [c 3]] b/a/c")).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("anything else is looked for by value")
    class ByValue {

        @Test
        @DisplayName("a string, a character or a block, each answering what follows it")
        void byValue() {
            // `Find_Block_Simple(...) + 1`
            assertThat(answerTo("b: [\"k\" 5] b/(\"k\")")).isEqualTo("5");
            assertThat(answerTo("b: reduce [#\"x\" 5] b/(#\"x\")")).isEqualTo("5");
            assertThat(answerTo("b: reduce [[1 2] 5] b/([1 2])")).isEqualTo("5");
        }

        @Test
        @DisplayName("and a value the block has not got answers none")
        void anAbsentValueIsNone() {
            assertThat(answerTo("b: [\"k\" 5] none? b/(\"other\")")).isEqualTo(TRUE);
            assertThat(answerTo("b: [a 1] none? b/(none)")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("writing through one")
    class Writing {

        @Test
        @DisplayName("replaces what the read would have answered")
        void itReplacesTheItem() {
            assertThat(answerTo("b: [a 1] b/a: 9 b")).isEqualTo("[a 9]");
            assertThat(answerTo("b: [a 1] b/2: 9 b")).isEqualTo("[a 9]");
            assertThat(answerTo("b: [a [c 3]] b/a/c: 9 b")).isEqualTo("[a [c 9]]");
        }

        @Test
        @DisplayName("in place, so another name for the block sees it")
        void itWritesInPlace() {
            assertThat(answerTo("b: [a 1] c: b b/a: 9 c")).isEqualTo("[a 9]");
        }

        @Test
        @DisplayName("a selector that finds nothing is refused rather than added")
        void anUnknownSelectorIsRefused() {
            // `if (pvs->setval) return PE_BAD_SELECT;` -- the one place a write
            // and a read part company. Appending would be the other defensible
            // answer and the C does not take it.
            assertThat(errorIdFrom("b: [a 1] b/zz: 5")).isEqualTo("invalid-path");
            assertThat(errorIdFrom("b: [a 1] b/9: 5")).isEqualTo("invalid-path");
        }

        @Test
        @DisplayName("except through position zero, which quietly does nothing")
        void positionZeroDoesNothing() {
            // `if (i == 0) return PE_NONE;` is tested before the write check,
            // so this one selector is not refused -- and a caller cannot tell it
            // from a write that worked.
            assertThat(errorIdFrom("b: [a 1] b/0: 5")).isEqualTo("no-error");
            assertThat(answerTo("b: [a 1] b/0: 5 b")).isEqualTo("[a 1]");
        }

        @Test
        @DisplayName("and a protected block is refused as an error a script can catch")
        void aProtectedBlockIsRefused() {
            // `if (pvs->setval) TRAP_PROTECT(VAL_SERIES(pvs->value));`. This
            // reached the host as a Java exception before, which
            // spec/embed.allium forbids outright: nothing a script does may
            // leave the interpreter as a throwable.
            assertThat(errorIdFrom("b: protect [a 1] b/a: 5")).isEqualTo("protected");
            assertThat(answerTo("b: protect [a 1] e: try [b/a: 5] b"))
                    .isEqualTo("[a 1]");
        }
    }
}
