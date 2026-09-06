package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CHECKSUM, COMPRESS and DECOMPRESS read a run of bytes, and a negative /PART
 * names the run behind the position rather than ahead of it.
 *
 * <p>{@code Partial1} in f-stubs.c turns the count round instead of refusing it
 * or reading it as nothing:
 *
 * <pre>
 * len = -len;
 * if (len &gt; (REBINT)VAL_INDEX(sval)) len = (REBINT)VAL_INDEX(sval);
 * VAL_INDEX(sval) -= (REBCNT)len;
 * </pre>
 *
 * <p>So the span always runs forwards from wherever the position lands, it is
 * clamped to what is actually behind, and at the head there is nothing behind
 * and the answer is empty. The three natives share that one function, which is
 * why one test file covers all three.
 *
 * <p>Rebol's own suite asks for it five times -- once in each of the ZLIB,
 * DEFLATE, GZIP, LZW and CRUSH groups of compress-test.r3, all spelled
 * {@code compress/part tail data 'zlib -4} -- and every one of them failed
 * here, because the count was clamped to zero and nothing was compressed.
 *
 * <p>Every expected value below was read from {@code ./r3-head} rather than
 * worked out, including the ones that look obvious.
 */
class ANegativePartOfBytesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String FOURTEEN_BYTES = """
            data: "test test test"
            """;

    @Nested
    @DisplayName("COMPRESS reads the bytes behind the position")
    class WhatCompressReads {

        @Test
        @DisplayName("four behind the tail are the last four bytes")
        void fourBehindTheTail() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part tail data 'zlib -4 'zlib"""))
                    .isEqualTo("#{74657374}");
        }

        @Test
        @DisplayName("one behind the tail is the last byte alone")
        void oneBehindTheTail() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part tail data 'zlib -1 'zlib"""))
                    .isEqualTo("#{74}");
        }

        @Test
        @DisplayName("a count as long as the string takes all of it")
        void exactlyAsManyAsAreBehind() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part tail data 'zlib -14 'zlib"""))
                    .isEqualTo("#{7465737420746573742074657374}");
        }

        @Test
        @DisplayName("one more than that is clamped rather than refused")
        void oneMoreThanIsBehind() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part tail data 'zlib -15 'zlib"""))
                    .isEqualTo("#{7465737420746573742074657374}");
        }

        @Test
        @DisplayName("far more than that is clamped the same way")
        void farMoreThanIsBehind() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part tail data 'zlib -99 'zlib"""))
                    .isEqualTo("#{7465737420746573742074657374}");
        }

        @Test
        @DisplayName("at the head there is nothing behind, so the answer is empty")
        void nothingIsBehindTheHead() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part data 'zlib -1 'zlib"""))
                    .isEqualTo("#{}");
        }

        @Test
        @DisplayName("from the middle it reaches back to the head and no further")
        void fromTheMiddle() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part skip data 5 'zlib -5 'zlib"""))
                    .isEqualTo("#{7465737420}");
        }

        @Test
        @DisplayName("a count of zero reads nothing, from anywhere")
        void zeroReadsNothing() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part tail data 'zlib 0 'zlib"""))
                    .isEqualTo("#{}");
        }

        @Test
        @DisplayName("a positive count still counts forwards")
        void aPositiveCountCountsForwards() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part data 'zlib 4 'zlib"""))
                    .isEqualTo("#{74657374}");
        }

        @Test
        @DisplayName("and a positive count past the tail is clamped to the tail")
        void aPositiveCountPastTheTail() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part data 'zlib 99 'zlib"""))
                    .isEqualTo("#{7465737420746573742074657374}");
        }

        @Test
        @DisplayName("a fraction is truncated towards zero before it is turned round")
        void aFractionIsTruncated() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part tail data 'zlib -4.9 'zlib"""))
                    .isEqualTo("#{74657374}");
        }

        @Test
        @DisplayName("a position earlier in the same series names a backwards span")
        void anEarlierPositionNamesABackwardsSpan() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part tail data 'zlib (skip data 4) 'zlib"""))
                    .isEqualTo("#{20746573742074657374}");
        }

        @Test
        @DisplayName("and the head as a limit reads the four bytes before position five")
        void theHeadAsALimit() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    decompress compress/part (skip data 4) 'zlib data 'zlib"""))
                    .isEqualTo("#{74657374}");
        }

        @Test
        @DisplayName("the caller's own position is left where it was")
        void theCallersPositionIsUntouched() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    here: tail data
                    compress/part here 'zlib -4
                    index? here""")).isEqualTo("15");
        }
    }

    @Nested
    @DisplayName("a binary is read the same way as a string")
    class OnABinary {

        private static final String FIVE_BYTES = """
                bytes: #{0102030405}
                """;

        @Test
        @DisplayName("two behind the tail are the last two octets")
        void twoBehindTheTail() {
            assertThat(answerTo(FIVE_BYTES + """
                    decompress compress/part tail bytes 'zlib -2 'zlib"""))
                    .isEqualTo("#{0405}");
        }

        @Test
        @DisplayName("more than there are is clamped to all of them")
        void moreThanThereAre() {
            assertThat(answerTo(FIVE_BYTES + """
                    decompress compress/part tail bytes 'zlib -9 'zlib"""))
                    .isEqualTo("#{0102030405}");
        }
    }

    @Nested
    @DisplayName("CHECKSUM and DECOMPRESS take it the same way")
    class TheOtherTwo {

        @Test
        @DisplayName("CHECKSUM of the last two octets is the checksum of those two")
        void checksumReadsBehind() {
            assertThat(answerTo("""
                    bytes: #{0102030405}
                    (checksum/part tail bytes 'crc32 -2) = checksum #{0405} 'crc32"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("CHECKSUM clamps to the head like everything else")
        void checksumClampsToTheHead() {
            assertThat(answerTo("""
                    bytes: #{0102030405}
                    (checksum/part tail bytes 'crc32 -9) = checksum bytes 'crc32"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("DECOMPRESS reads the tail of a stream, and refuses it as a stream")
        void decompressReadsBehind() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    failure: try [decompress/part tail (compress data 'zlib) 'zlib -6]
                    failure/id""")).isEqualTo("bad-press");
        }

        @Test
        @DisplayName("and reading the whole stream backwards from its tail works")
        void decompressReadsTheWholeStreamBackwards() {
            assertThat(answerTo(FOURTEEN_BYTES + """
                    packed: compress data 'zlib
                    decompress/part tail packed 'zlib (negate length? packed)"""))
                    .isEqualTo("#{7465737420746573742074657374}");
        }
    }
}
