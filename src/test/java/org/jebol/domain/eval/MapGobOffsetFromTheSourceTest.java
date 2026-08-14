package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * MAP-GOB-OFFSET, from {@code REBNATIVE(map_gob_offset)} and
 * {@code Map_Gob_Inner} in {@code n-data.c}.
 *
 * <p>What a window system asks when a click arrives: given a point in the
 * outermost gob, which gob was actually clicked and where in that gob. So it
 * walks <em>down</em> the tree by default, and /REVERSE walks back up.
 *
 * <p>Both directions are the same two lines of arithmetic seen from either end.
 * Going down, each gob it enters has its offset subtracted from the point. Going
 * up, each gob it leaves has its offset added.
 *
 * <p>Two things about the descent are not obvious from the name. It searches each
 * pane <em>backwards</em> -- {@code gop = GOB_HEAD(gob) + len - 1} and then
 * {@code gop--} -- so the child added last wins where two overlap, which is what
 * "topmost" means on a screen. And the rectangle test is half-open:
 * {@code xo >= x + GOB_X} and {@code xo < x + GOB_X + GOB_W}, so a point on the
 * left edge is inside and a point on the right edge is not.
 *
 * <p>The answer is a two-item block: the gob it reached and the point in that
 * gob's own coordinates. {@code Return_Gob_Pair} builds it.
 */
class MapGobOffsetFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    /** A parent at nothing, holding one child at 10x10 that is 20 by 20. */
    private static final String ONE_CHILD =
            "p: make gob! [size: 100x100] "
            + "c: make gob! [offset: 10x10 size: 20x20] append p c ";

    @Nested
    @DisplayName("the answer's shape")
    class TheAnswer {

        @Test
        @DisplayName("a block of the gob it reached and the point inside it")
        void aGobAndAPair() {
            assertThat(answerTo("length? map-gob-offset make gob! [] 5x5"))
                    .isEqualTo("2");
            assertThat(answerTo(
                    "b: map-gob-offset make gob! [] 5x5 reduce [gob? b/1 pair? b/2]"))
                    .isEqualTo("[#(true) #(true)]");
        }

        @Test
        @DisplayName("a gob with no children answers itself and the point untouched")
        void nothingToDescendInto() {
            assertThat(answerTo(
                    "g: make gob! [] b: map-gob-offset g 5x7 same? g b/1"))
                    .isEqualTo(TRUE);
            assertThat(answerTo("second map-gob-offset make gob! [] 5x7"))
                    .isEqualTo("5x7");
        }

        @Test
        @DisplayName("and it takes a gob and a pair, and nothing else")
        void itsArguments() {
            assertThat(errorIdFrom("map-gob-offset 1 5x5")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("map-gob-offset make gob! [] 5")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("walking down")
    class Descending {

        @Test
        @DisplayName("a point inside a child answers the child, in the child's coordinates")
        void oneLevelDown() {
            assertThat(answerTo(ONE_CHILD + "same? c first map-gob-offset p 15x15"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(ONE_CHILD + "second map-gob-offset p 15x15"))
                    .isEqualTo("5x5");
        }

        @Test
        @DisplayName("a point outside every child stops at the parent")
        void nothingHoldsThePoint() {
            assertThat(answerTo(ONE_CHILD + "same? p first map-gob-offset p 50x50"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(ONE_CHILD + "second map-gob-offset p 50x50"))
                    .isEqualTo("50x50");
        }

        @Test
        @DisplayName("the rectangle is closed on the left and open on the right")
        void theEdges() {
            assertThat(answerTo(ONE_CHILD + "same? c first map-gob-offset p 10x10"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(ONE_CHILD + "same? c first map-gob-offset p 29x29"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(ONE_CHILD + "same? p first map-gob-offset p 30x30"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(ONE_CHILD + "same? p first map-gob-offset p 9x9"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("both halves have to hold, not either one")
        void bothHalvesAtOnce() {
            assertThat(answerTo(ONE_CHILD + "same? p first map-gob-offset p 15x50"))
                    .isEqualTo(TRUE);
            assertThat(answerTo(ONE_CHILD + "same? p first map-gob-offset p 50x15"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("where two children overlap, the last one added wins")
        void theTopmostChildWins() {
            assertThat(answerTo(
                    "p: make gob! [size: 100x100] "
                    + "under: make gob! [offset: 0x0 size: 50x50] "
                    + "over: make gob! [offset: 0x0 size: 50x50] "
                    + "append p under append p over "
                    + "same? over first map-gob-offset p 10x10")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it keeps going as deep as the tree does")
        void severalLevelsDown() {
            assertThat(answerTo(
                    "p: make gob! [size: 100x100] "
                    + "mid: make gob! [offset: 5x5 size: 50x50] "
                    + "deep: make gob! [offset: 10x10 size: 20x20] "
                    + "append p mid append mid deep "
                    + "same? deep first map-gob-offset p 20x20")).isEqualTo(TRUE);
            assertThat(answerTo(
                    "p: make gob! [size: 100x100] "
                    + "mid: make gob! [offset: 5x5 size: 50x50] "
                    + "deep: make gob! [offset: 10x10 size: 20x20] "
                    + "append p mid append mid deep "
                    + "second map-gob-offset p 20x20")).isEqualTo("5x5");
        }
    }

    @Nested
    @DisplayName("walking up, with /REVERSE")
    class Ascending {

        @Test
        @DisplayName("each parent's offset is added on the way out")
        void offsetsAccumulate() {
            assertThat(answerTo(ONE_CHILD
                    + "second map-gob-offset/reverse c 5x5")).isEqualTo("15x15");
            assertThat(answerTo(ONE_CHILD
                    + "same? p first map-gob-offset/reverse c 5x5")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it is the exact inverse of walking down")
        void itUndoesTheDescent() {
            assertThat(answerTo(ONE_CHILD
                    + "b: map-gob-offset p 15x15 "
                    + "second map-gob-offset/reverse b/1 b/2")).isEqualTo("15x15");
        }

        @Test
        @DisplayName("a gob with no parent answers itself and the point untouched")
        void nothingToAscendTo() {
            assertThat(answerTo(
                    "g: make gob! [offset: 9x9] second map-gob-offset/reverse g 5x5"))
                    .isEqualTo("5x5");
            assertThat(answerTo(
                    "g: make gob! [offset: 9x9] same? g first map-gob-offset/reverse g 5x5"))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and it climbs the whole way, not one step")
        void allTheWayUp() {
            assertThat(answerTo(
                    "p: make gob! [] mid: make gob! [offset: 5x5] "
                    + "deep: make gob! [offset: 10x10] "
                    + "append p mid append mid deep "
                    + "second map-gob-offset/reverse deep 1x1")).isEqualTo("16x16");
            assertThat(answerTo(
                    "p: make gob! [] mid: make gob! [offset: 5x5] "
                    + "deep: make gob! [offset: 10x10] "
                    + "append p mid append mid deep "
                    + "same? p first map-gob-offset/reverse deep 1x1")).isEqualTo(TRUE);
        }
    }
}
