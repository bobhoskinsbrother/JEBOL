package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading and writing a series by position: PICK, PICKZ, PUT, and what a binary
 * will accept.
 *
 * <p>Read out of {@code Pick_Block} and the PUT arm of {@code t-block.c},
 * {@code REBNATIVE(pickz)} in {@code f-series.c}, and the byte range checks in
 * {@code t-string.c}. Every case checked against the R3 binary.
 *
 * <p>Four things here are easy to get wrong in the convenient direction. There
 * is no position zero and a negative position counts back from where the series
 * is, so {@code pick tail s -1} is the last item. PICKZ renumbers forwards only,
 * so {@code pickz s -1} and {@code pick s -1} are one question. PUT looks at
 * every position rather than every other one, so it writes after the first
 * matching key and not the first key of a pair. And a number that will not fit
 * in a byte is refused rather than truncated -- {@code a/1: 400} used to store
 * 144 and answer 400.
 *
 * <p>Specified in {@code spec/natives.allium} under "Reading and writing a
 * series by position".
 */
class SeriesByPositionFromTheSourceTest {

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
    @DisplayName("PICK")
    class Picking {

        @Test
        @DisplayName("counts from one, and answers none outside the series")
        void countingFromOne() {
            assertThat(answerTo("b: [1 2 3] reduce [pick b 1 pick b 2 "
                    + "none? pick b -1 none? pick b 0 none? pick b 10]"))
                    .isEqualTo("[1 2 #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("a negative position counts back from where the series is")
        void aNegativePositionCountsBack() {
            assertThat(answerTo("b: skip [1 2 3] 2 reduce [none? pick b 2 pick b 1 "
                    + "none? pick b 0 pick b -1 pick b -2]"))
                    .isEqualTo("[#(true) 3 #(true) 2 1]");
        }

        @Test
        @DisplayName("so the last item of a series is PICK TAIL of it at minus one")
        void theLastItemFromTheTail() {
            assertThat(answerTo("b: [1 2 3] reduce [none? pick tail b 0 pick tail b -1]"))
                    .isEqualTo("[#(true) 3]");
            assertThat(answerTo("s: \"123\" reduce [none? pick tail s 0 pick tail s -1]"))
                    .isEqualTo("[#(true) #\"3\"]");
        }

        @Test
        @DisplayName("and a string answers characters by the same rules")
        void aStringTheSameWay() {
            assertThat(answerTo("s: skip \"123\" 2 reduce [none? pick s 2 pick s 1 "
                    + "pick s -1 pick s -2]"))
                    .isEqualTo("[#(true) #\"3\" #\"2\" #\"1\"]");
        }
    }

    @Nested
    @DisplayName("PICKZ")
    class PickingFromZero {

        @Test
        @DisplayName("renumbers forwards")
        void itRenumbersForwards() {
            assertThat(answerTo("b: [1 2 3] reduce [none? pickz b -1 pickz b 0 "
                    + "pickz b 1 pickz b 2 none? pickz b 3]"))
                    .isEqualTo("[#(true) 1 2 3 #(true)]");
        }

        @Test
        @DisplayName("and leaves a negative index to mean what it means to PICK")
        void aNegativeIndexIsUntouched() {
            assertThat(answerTo("b: skip [1 2 3] 2 reduce [pickz b -2 pickz b -1 "
                    + "pickz b 0 none? pickz b 1]"))
                    .isEqualTo("[1 2 3 #(true)]");
            assertThat(answerTo("s: skip \"123\" 2 (pickz s -1) = pick s -1"))
                    .isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("PUT into a block")
    class Putting {

        @Test
        @DisplayName("writes after the key, and answers the value")
        void itWritesAfterTheKey() {
            assertThat(answerTo("v: [a 1 b 2 c] reduce [put v 'a 3 put v 'b 4]"))
                    .isEqualTo("[3 4]");
            assertThat(answerTo("v: [a 1 b 2] put v 'a 3 v")).isEqualTo("[a 3 b 2]");
        }

        @Test
        @DisplayName("looking at every position, not every other one")
        void everyPositionIsAKey() {
            assertThat(answerTo("v: [a b b c] put v 'b 0 v")).isEqualTo("[a b 0 c]");
        }

        @Test
        @DisplayName("and /SKIP is how a caller asks for records instead")
        void skipAsksForRecords() {
            assertThat(answerTo("v: [a b b c] put/skip v 'b 0 2 v"))
                    .isEqualTo("[a b b 0]");
        }

        @Test
        @DisplayName("a key with nothing after it grows the block by one")
        void aKeyAtTheTailGrowsTheBlock() {
            assertThat(answerTo("v: [a 1 c] put v 'c 5 v")).isEqualTo("[a 1 c 5]");
        }

        @Test
        @DisplayName("and a key the block has not got is added with its value")
        void anUnknownKeyIsAdded() {
            assertThat(answerTo("v: [a 1] put v 'd 6 v")).isEqualTo("[a 1 d 6]");
            assertThat(answerTo("v: [] put v 'd 6 v")).isEqualTo("[d 6]");
        }
    }

    @Nested
    @DisplayName("what a binary will accept as a byte")
    class BinaryBytes {

        @Test
        @DisplayName("POKE refuses a number that will not fit")
        void pokeRefusesABigNumber() {
            assertThat(answerTo("a: #{0102} reduce [poke a 1 3 a]"))
                    .isEqualTo("[3 #{0302}]");
            assertThat(errorIdFrom("poke #{0102} 1 300")).isEqualTo("out-of-range");
            assertThat(errorIdFrom("poke #{0102} 1 -1")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and so does a write through a path")
        void aPathWriteRefusesToo() {
            assertThat(answerTo("a: #{0102} a/1: 4 a")).isEqualTo("#{0402}");
            assertThat(errorIdFrom("a: #{0102} a/1: 400")).isEqualTo("out-of-range");
            assertThat(errorIdFrom("a: #{0102} a/1: -1")).isEqualTo("bad-path-set");
            assertThat(answerTo("a: #{0102} e: try [a/1: 400] a")).isEqualTo("#{0102}");
        }

        @Test
        @DisplayName("CHANGE writes bytes and answers the position after them")
        void changeWritesBytes() {
            assertThat(answerTo("a: #{0102} reduce [change a 5 a]"))
                    .isEqualTo("[#{02} #{0502}]");
            assertThat(errorIdFrom("change #{0102} 500")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("and grows the binary where the bytes run past the end")
        void changeGrowsTheBinary() {
            assertThat(answerTo("b: #{00} change b 1.2.3.4 b"))
                    .isEqualTo("#{01020304}");
            assertThat(answerTo("b: #{00000000} reduce [change b 1.2.3 b]"))
                    .isEqualTo("[#{00} #{01020300}]");
        }

        @Test
        @DisplayName("/PART takes bytes out before putting the new ones in")
        void partTakesBytesOut() {
            assertThat(answerTo("b: #{AABBCCDD} reduce [change/part b 1.2.3.4 2 b]"))
                    .isEqualTo("[#{CCDD} #{01020304CCDD}]");
        }
    }

    @Nested
    @DisplayName("INDEX? and INDEXZ? answer where a series is")
    class WhereTheSeriesIs {

        @Test
        @DisplayName("one counting from one and the other from zero")
        void theTwoNumberings() {
            assertThat(answerTo("index? skip [1 2 3] 2")).isEqualTo("3");
            assertThat(answerTo("indexz? skip [1 2 3] 2")).isEqualTo("2");
            assertThat(answerTo("index? [1 2 3]")).isEqualTo("1");
            assertThat(answerTo("indexz? [1 2 3]")).isEqualTo("0");
        }

        @Test
        @DisplayName("/XY asks for the position as a pair, which only an image answers")
        void theXyRefinementIsDeclaredAndIdle() {
            assertThat(answerTo("index?/xy skip \"abc\" 2")).isEqualTo("3");
            assertThat(answerTo("indexz?/xy skip \"abc\" 2")).isEqualTo("2");
            assertThat(answerTo("index?/xy [1 2 3]")).isEqualTo("1");
        }

        @Test
        @DisplayName("INDEX? tolerates none and INDEXZ? does not")
        void onlyOneOfThemTakesNone() {
            assertThat(answerTo("none? index? none")).isEqualTo("#(true)");
            assertThat(errorIdFrom("indexz? none")).isEqualTo("cannot-use");
        }

        @Test
        @DisplayName("and neither takes something that is not on the declared list")
        void neitherTakesAnythingElse() {
            assertThat(errorIdFrom("index? 5")).isEqualTo("expect-arg");
            assertThat(errorIdFrom("indexz? 5")).isEqualTo("expect-arg");
        }
    }
}
