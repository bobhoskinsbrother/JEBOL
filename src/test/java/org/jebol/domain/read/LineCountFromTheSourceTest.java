package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the reader starts counting lines, and what it reports when it stops.
 *
 * <p>From {@code REBNATIVE(transcode)} in {@code rebol3-source/src/core/l-scan.c}:
 *
 * <pre>
 * if (line) {
 *     if (0 &gt;= VAL_INT64(count)) Trap1(RE_OUT_OF_RANGE, count);
 *     scan_state.line_count = VAL_UNT32(count);
 * }
 * </pre>
 *
 * <p>and, at the end of the same function:
 *
 * <pre>
 * if (line) {
 *     SET_INTEGER(count, scan_state.line_count);
 *     Append_Val(blk, count);
 * }
 * </pre>
 *
 * <p>A source carries no record of where it came from, so a reader handed the middle
 * of a file would call that fragment's first line line one. Every error after the
 * first would then name the wrong place. The caller keeps the count instead: it hands
 * a number in and gets one back, and the one that comes back is what it hands in next.
 *
 * <p>The count comes back only when the caller is walking the source, because the C
 * appends it to the same block that carries the unread text, and that block is only
 * built when a refinement asked to stop after one value.
 */
class LineCountFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The line a failure names, so a mistaken count shows up as a number. */
    private static String lineNamedBy(String call) {
        return answerTo("e: try [" + call + "] "
                + "either error? e ["
                + "to integer! copy/part (skip find e/near \"line\" 5) (find e/near \")\")"
                + "] [0]");
    }

    private static String errorIdOf(String call) {
        return answerTo("e: try [" + call + "] "
                + "either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("the count starts at one unless the caller says otherwise")
    class WhereCountingStarts {

        @Test
        @DisplayName("with no line given, the first line of the source is line one")
        void theDefaultIsLineOne() {
            assertThat(lineNamedBy("""
                    transcode "1d\"""")).isEqualTo("1");
            assertThat(lineNamedBy("""
                    transcode "1^/1d\"""")).isEqualTo("2");
        }

        @Test
        @DisplayName("one is the lowest a caller may ask for, and two is the next")
        void theLowestStartIsOne() {
            assertThat(lineNamedBy("""
                    transcode/line "1d" 1""")).isEqualTo("1");
            assertThat(lineNamedBy("""
                    transcode/line "1d" 2""")).isEqualTo("2");
        }

        @Test
        @DisplayName("and a start further down the file carries through")
        void aStartFurtherDownTheFile() {
            assertThat(answerTo("""
                    all [error? e: try [transcode/line "1 1d" 10] \
                    e/near = "(line 10) 1 1d"]""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    all [error? e: try [transcode/line "1^/1d" 10] \
                    e/near = "(line 11) 1d"]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("zero is refused, and so is anything below it")
        void zeroAndBelowAreRefused() {
            assertThat(errorIdOf("""
                    transcode/line "1 1d" 0""")).isEqualTo("out-of-range");
            assertThat(errorIdOf("""
                    transcode/line "1 1d" -1""")).isEqualTo("out-of-range");
            assertThat(errorIdOf("""
                    transcode/line "1 2" 0""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("and anything that is not a whole number is refused as an argument")
        void aCountThatIsNotAWholeNumberIsRefused() {
            assertThat(errorIdOf("""
                    transcode/line "1 1d" "ten\"""")).isEqualTo("expect-arg");
            assertThat(errorIdOf("""
                    transcode/line "1 1d" 1.5""")).isEqualTo("expect-arg");
            assertThat(errorIdOf("""
                    transcode/line "1 1d" none""")).isEqualTo("expect-arg");
            assertThat(errorIdOf("""
                    transcode/line "1 1d" [10]""")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("and every kind of line break adds one")
    class WhatCountsAsALine {

        @Test
        @DisplayName("several breaks all count")
        void severalBreaksAllCount() {
            assertThat(lineNamedBy("""
                    transcode "1^/^/^/1d\"""")).isEqualTo("4");
            assertThat(lineNamedBy("""
                    transcode/line "1^/^/^/1d" 10""")).isEqualTo("13");
        }

        @Test
        @DisplayName("a break inside a string counts, because the reader crossed it")
        void aBreakInsideAStringCounts() {
            assertThat(lineNamedBy("""
                    transcode rejoin ["{x" newline "y} 1d"]""")).isEqualTo("2");
            assertThat(lineNamedBy("""
                    transcode rejoin ["{x" newline newline "y} 1d"]"""))
                    .isEqualTo("3");
        }

        @Test
        @DisplayName("a bare carriage return is a line of its own")
        void aCarriageReturnIsALineBreakToo() {
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" cr "1d"]""")).isEqualTo("2");
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" cr cr "1d"]""")).isEqualTo("3");
        }

        @Test
        @DisplayName("but a carriage return and a line feed together are one line")
        void theTwoTogetherCountOnce() {
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" cr lf "1d"]""")).isEqualTo("2");
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" cr lf cr lf "1d"]""")).isEqualTo("3");
        }

        @Test
        @DisplayName("and a line feed followed by a carriage return is two lines")
        void theTwoTheOtherWayRoundAreTwoLines() {
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" lf cr "1d"]""")).isEqualTo("3");
        }

        @Test
        @DisplayName("and a break inside a binary counts")
        void aBreakInsideABinaryCounts() {
            assertThat(lineNamedBy("""
                    transcode rejoin ["#{00" newline "00} 1d"]""")).isEqualTo("2");
        }

        @Test
        @DisplayName("and the break that ends a comment counts")
        void aBreakAfterACommentCounts() {
            assertThat(lineNamedBy("""
                    transcode ";note^/1d\"""")).isEqualTo("2");
        }
    }

    @Nested
    @DisplayName("and the caller walking a source is handed the count back")
    class WhatComesBack {

        @Test
        @DisplayName("as a third item, so a walk can pass it straight in again")
        void theWalkHandsBackTheLineItStoppedOn() {
            assertThat(answerTo("""
                    all [
                        code: "1^/2" line: 1
                        set [value code line] transcode/next/line :code :line
                        value = 1 line = 1
                        set [value code line] transcode/next/line :code :line
                        value = 2 line = 2
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a step that crosses no break reports the line it started on")
        void aWalkThatCrossesNoBreak() {
            assertThat(answerTo("""
                    [1 " 2" 1] = transcode/next/line "1 2" 1""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a walk that starts further down keeps the offset")
        void aWalkThatStartsFurtherDown() {
            assertThat(answerTo("""
                    [1 " 2" 40] = transcode/next/line "1 2" 40""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [1 "^/2" 40] = transcode/next/line "1^/2" 40""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but it does not come back to a caller reading the whole source")
        void theCountOnlyComesBackWhenWalking() {
            assertThat(answerTo("""
                    [1 2] = transcode/line "1 2" 10""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    1 = transcode/one/line "1 2" 10""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a value spanning lines reports the line it ended on")
        void aValueThatSpansLines() {
            assertThat(answerTo("""
                    all [
                        result: transcode/next/line rejoin ["[1" newline "2] 3"] 1
                        result/1 = [1 2]
                        result/3 = 2
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and asking for a value where there is none left still raises past-end")
        void nothingLeftToWalk() {
            assertThat(errorIdOf("""
                    transcode/next/line "" 10""")).isEqualTo("past-end");
            assertThat(answerTo("""
                    all [error? e: transcode/next/line/error "" 10 \
                    e/id = 'past-end]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a source with nothing in it is still an empty block")
        void anEmptySourceIsStillAnEmptyBlock() {
            assertThat(answerTo("""
                    [] = transcode/line "" 10""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [] = transcode/line "   " 10""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and the offset reaches the other refinements")
    class AlongsideTheRest {

        @Test
        @DisplayName("the error handed back as a value names the offset line")
        void theOffsetReachesTheErrorValue() {
            assertThat(answerTo("""
                    all [
                        blk: transcode/error/line "1d" 10
                        error? e: blk/1
                        e/near = "(line 10) 1d"
                    ]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and both arguments arrive in the order they were declared")
        void bothArgumentsAtOnce() {
            assertThat(answerTo("""
                    [1 1] = transcode/line/part "1 1d" 10 3""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    all [error? e: try [transcode/line/part "1 1d" 10 4] \
                    e/near = "(line 10) 1 1d"]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a bound of zero reads nothing, which is legal")
        void aBoundOfZeroReadsNothing() {
            assertThat(answerTo("""
                    [] = transcode/part "1 2" 0""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a bound of one reads the first character, and a longer one reads it all")
        void theBoundEitherSideOfZero() {
            assertThat(answerTo("""
                    [1] = transcode/part "1 2" 1""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [1 2] = transcode/part "1 2" 99""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a negative bound is refused")
        void aNegativeBoundIsRefused() {
            assertThat(errorIdOf("""
                    transcode/part "1 2" -1""")).isEqualTo("out-of-range");
        }

        @Test
        @DisplayName("and a bound that is not a whole number is refused as an argument")
        void aBoundThatIsNotAWholeNumberIsRefused() {
            assertThat(errorIdOf("""
                    transcode/part "1 2" "three\"""")).isEqualTo("expect-arg");
            assertThat(errorIdOf("""
                    transcode/part "1 2" 1.5""")).isEqualTo("expect-arg");
        }
    }
}
