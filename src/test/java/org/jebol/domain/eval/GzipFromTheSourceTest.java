package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ten bytes a gzip member opens with, and which of them the level decides.
 *
 * <p>Rebol writes the header itself in {@code gzip_compress.c} and only the
 * ninth byte varies:
 *
 * <pre>
 * xfl = 0;
 * if (compression_level &lt; 2) xfl |= GZIP_XFL_FASTEST_COMPRESSION;  // 0x04
 * else if (compression_level &gt;= 8) xfl |= GZIP_XFL_SLOWEST_COMPRESSION;  // 0x02
 * </pre>
 *
 * <p>The tenth is {@code GZIP_OS_UNKNOWN}, which is {@code FF} and not zero.
 * This wrote seven zero bytes after the first three and so got both wrong.
 *
 * <p>The level itself never reached the compressor at all, which is the defect
 * Rebol's suite catches twice: it asks for {@code compress/level "" 'gzip 0}
 * and expects stored blocks, and this answered whatever the default level
 * produced.
 *
 * <p>Two differences from a real 3.22.5 remain and are not defects here.
 * Rebol compresses with libdeflate, which has twelve levels where
 * {@code java.util.zip} has nine, so a level above nine is clamped to nine
 * rather than to twelve; and a level nobody asked for is the slowest one in
 * both, which is twelve there and nine here. Both build valid gzip that either
 * side reads, and neither is byte for byte the other above level one.
 */
class GzipFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("the bytes it writes")
    class TheBytes {

        @Test
        @DisplayName("nothing, at level zero, is a header, a stored block and a trailer")
        void nothingAtLevelZero() {
            assertThat(answerTo("""
                    compress/level "" 'gzip 0"""))
                    .isEqualTo("#{1F8B08000000000004FF010000FFFF0000000000000000}");
        }

        @Test
        @DisplayName("and fourteen bytes at level zero are stored as they stand")
        void fourteenBytesAtLevelZero() {
            assertThat(answerTo("""
                    (compress/level {test test test} 'gzip 0) =
                        #{1F8B08000000000004FF010E00F1FF74657374207465737420
                          74657374026A5B230E000000}""")).isEqualTo("#(true)");
        }

        /**
         * The checksum here is the one the gzip format names, which is not the
         * one {@code checksum "test test test" 'crc32} answers: that is Rebol's
         * own table and gives 593193474 where this trailer holds 593495554.
         */
        @Test
        @DisplayName("the trailer is the CRC-32 and then the length, little endian")
        void theTrailerIsTheChecksumAndTheLength() {
            assertThat(answerTo("""
                    packed: compress/level "test test test" 'gzip 0
                    skip packed (length? packed) - 8"""))
                    .isEqualTo("#{026A5B230E000000}");
        }
    }

    @Nested
    @DisplayName("the ninth byte says how hard the compressor was asked to try")
    class TheExtraFlagsByte {

        private static String extraFlagsAtLevel(String level) {
            return answerTo(
                    "to integer! pick (compress/level {test test test} 'gzip "
                            + level + ") 9");
        }

        @Test
        @DisplayName("four below level two")
        void fourBelowLevelTwo() {
            assertThat(extraFlagsAtLevel("0")).isEqualTo("4");
            assertThat(extraFlagsAtLevel("1")).isEqualTo("4");
        }

        @Test
        @DisplayName("nothing between two and seven")
        void nothingInTheMiddle() {
            assertThat(extraFlagsAtLevel("2")).isEqualTo("0");
            assertThat(extraFlagsAtLevel("7")).isEqualTo("0");
        }

        @Test
        @DisplayName("two at eight and above")
        void twoAtTheTop() {
            assertThat(extraFlagsAtLevel("8")).isEqualTo("2");
            assertThat(extraFlagsAtLevel("9")).isEqualTo("2");
        }

        @Test
        @DisplayName("and two when nobody asked, because that is the slowest")
        void twoWhenNobodyAsked() {
            assertThat(answerTo("""
                    to integer! pick (compress "test test test" 'gzip) 9"""))
                    .isEqualTo("2");
        }

        @Test
        @DisplayName("the tenth byte is FF, the operating system nobody named")
        void theOperatingSystemIsUnknown() {
            assertThat(answerTo("""
                    to integer! pick (compress "test test test" 'gzip) 10"""))
                    .isEqualTo("255");
        }
    }

    @Nested
    @DisplayName("a level outside the range this build has")
    class OutsideTheRange {

        @Test
        @DisplayName("above the top is clamped rather than refused")
        void aboveTheTop() {
            assertThat(answerTo("""
                    "test test test" = to string! decompress
                        compress/level "test test test" 'gzip 99 'gzip"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("below the bottom is the slowest, as an unasked-for level is")
        void belowTheBottom() {
            assertThat(answerTo("""
                    (compress/level "test test test" 'gzip -1)
                        = compress "test test test" 'gzip"""))
                    .isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("reading it back")
    class ReadingItBack {

        @Test
        @DisplayName("what went in comes out, at every level")
        void everyLevelRoundTrips() {
            assertThat(answerTo("""
                    text: {Lorem ipsum dolor sit amet, consectetur adipisici elit,
                    sed eiusmod tempor incidunt ut labore et dolore magna aliqua.}
                    collect [
                        repeat level 10 [
                            keep text = to string! decompress
                                compress/level text 'gzip (level - 1) 'gzip
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)"
                            + " #(true) #(true) #(true) #(true) #(true)]");
        }

        @Test
        @DisplayName("a real 3.22.5 member at level zero reads here")
        void aRealMemberReadsHere() {
            assertThat(answerTo("""
                    to string! decompress
                        #{1F8B08000000000004FF010E00F1FF74657374207465737420
                        74657374026A5B230E000000} 'gzip"""))
                    .isEqualTo("\"test test test\"");
        }
    }
}
