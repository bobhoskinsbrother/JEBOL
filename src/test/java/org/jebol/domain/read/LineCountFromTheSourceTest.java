package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            // Rebol's own two assertions for the refinement, which are the pair that
            // proves the offset is added rather than replacing the count.
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
            // `if (0 >= VAL_INT64(count)) Trap1(RE_OUT_OF_RANGE, count)`. A line number
            // is a counting number, so zero is not "do not count" and minus one is not
            // "count backwards".
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
            // A braced string spans lines, and the lines it spans are lines of the
            // file like any other. Built with REJOIN because a caret escape in the
            // outer source would be read by the outer reader, not this one.
            assertThat(lineNamedBy("""
                    transcode rejoin ["{x" newline "y} 1d"]""")).isEqualTo("2");
            assertThat(lineNamedBy("""
                    transcode rejoin ["{x" newline newline "y} 1d"]"""))
                    .isEqualTo("3");
        }

        @Test
        @DisplayName("a bare carriage return is a line of its own")
        void aCarriageReturnIsALineBreakToo() {
            // `case LEX_DELIMIT_RETURN: if (cp[1] == LF) cp++; /* fall thru */` into
            // the line feed's `line_count++`. So a carriage return counts, and it
            // ends the token as a line feed does.
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" cr "1d"]""")).isEqualTo("2");
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" cr cr "1d"]""")).isEqualTo("3");
        }

        @Test
        @DisplayName("but a carriage return and a line feed together are one line")
        void theTwoTogetherCountOnce() {
            // The `if (cp[1] == LF) cp++` steps over the line feed before the count
            // rises, so a file written on Windows is not counted twice over.
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" cr lf "1d"]""")).isEqualTo("2");
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" cr lf cr lf "1d"]""")).isEqualTo("3");
        }

        @Test
        @DisplayName("and a line feed followed by a carriage return is two lines")
        void theTwoTheOtherWayRoundAreTwoLines() {
            // Only the carriage return looks ahead for its partner. A line feed
            // counts and then the carriage return counts again, so the pair in this
            // order is two lines where the other order is one.
            assertThat(lineNamedBy("""
                    transcode rejoin ["1" lf cr "1d"]""")).isEqualTo("3");
        }

        @Test
        @DisplayName("and a break inside a binary counts")
        void aBreakInsideABinaryCounts() {
            // A long binary is written across lines with a note beside it, which is
            // the reason whitespace inside the braces is ignored at all. Ignored for
            // the value is not ignored for the count.
            assertThat(lineNamedBy("""
                    transcode rejoin ["#{00" newline "00} 1d"]""")).isEqualTo("2");
        }

        @Test
        @DisplayName("and the break that ends a comment counts")
        void aBreakAfterACommentCounts() {
            // `while (NOT_NEWLINE(*cp)) cp++; if (*cp == LF) goto line_feed;` -- the
            // comment eats the line and then the break is counted anyway.
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
            // Rebol's own walk, which is the whole reason the count comes back at
            // all. The break belongs to the step that crosses it: the value on line
            // one reports one, and the step that crosses the break reports two.
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
            // The C appends it to the same block that carries the unread text, and
            // builds that block only when a refinement asked to stop after one value.
            // So asking where the reader finished a whole source is not a question
            // this native answers.
            assertThat(answerTo("""
                    [1 2] = transcode/line "1 2" 10""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    1 = transcode/one/line "1 2" 10""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a value spanning lines reports the line it ended on")
        void aValueThatSpansLines() {
            // The step reports where the reader is afterwards, not where the value
            // began. A block written across two lines leaves the reader on the second.
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
            // The count changes nothing about the failure: the refinement adds a
            // number to the answer, and there is no answer to add it to.
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
            // The count is declared before the length, so a native reading them by
            // position gets them the wrong way round the moment both are asked for.
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
            // `if (0 > VAL_INT64(length)) Trap1(RE_OUT_OF_RANGE, length)`. Zero is
            // permitted here and refused for the line number, which is the pair worth
            // reading together: no characters is a legal amount to read, and no lines
            // is not a line.
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
