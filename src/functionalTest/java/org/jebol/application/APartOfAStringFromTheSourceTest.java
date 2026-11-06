package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class APartOfAStringFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String gzipped(String partOf, String count) {
        return answerTo("decompress compress/part " + partOf + " 'gzip " + count + " 'gzip");
    }

    @ParameterizedTest(name = "compress/part {0} {1} keeps {2}")
    @CsvSource(delimiter = '|', value = {
            "{ěšč}          | 1   | #{C49B}",
            "skip {ěšč} 1   | 1   | #{C5A1}",
            "{ěšč}          | 100 | #{C49BC5A1C48D}",
            "{ěšč}          | 0   | #{}"
    })
    @DisplayName("A count is in characters")
    void countsCharacters(String partOf, String count, String kept) {
        assertThat(gzipped(partOf, count)).isEqualTo(kept);
    }

    @ParameterizedTest(name = "compress/part {0} {1} keeps {2}")
    @CsvSource(delimiter = '|', value = {
            "skip {ěšč} 1   | -1 | #{C5A1C48D}",
            "skip {ěšč} 2   | -1 | #{C48D}",
            "skip {ěšč} 2   | -2 | #{C48D}",
            "skip {ěšč} 3   | -1 | #{}",
            "{ěšč}          | -1 | #{C49BC5A1C48D}",
            "skip {aěc} 2   | -1 | #{63}"
    })
    @DisplayName("A negative count on a string holding more than ASCII wraps in the C, so it reads the rest, forwards")
    void readsTheRestForANegativeCount(String partOf, String count, String kept) {
        assertThat(gzipped(partOf, count)).isEqualTo(kept);
    }

    @ParameterizedTest(name = "compress/part {0} {1} keeps {2}")
    @CsvSource(delimiter = '|', value = {
            "skip {abc} 2      | -1 | #{62}",
            "{abc}             | -1 | #{}",
            "skip #{616263} 2  | -1 | #{62}"
    })
    @DisplayName("On ASCII and on a binary a negative count reaches back, as ever")
    void reachesBackOnAscii(String partOf, String count, String kept) {
        assertThat(gzipped(partOf, count)).isEqualTo(kept);
    }

    @ParameterizedTest(name = "compress/part {0} {1} keeps {2}")
    @CsvSource(delimiter = '|', value = {
            "{ě^@b} | 1 | #{C49B}",
            "{ě^@b} | 2 | #{C49B}",
            "{ě^@b} | 3 | #{C49B}",
            "{a^@b} | 3 | #{610062}"
    })
    @DisplayName("A count stops at a NUL in a string holding more than ASCII, and not in one holding only ASCII")
    void stopsAtZeroOnlyBeyondAscii(String partOf, String count, String kept) {
        assertThat(gzipped(partOf, count)).isEqualTo(kept);
    }

    @Test
    @DisplayName("Without /part the whole string is read, a NUL included")
    void readsAZeroWithoutPart() {
        assertThat(answerTo("decompress compress {ě^@b} 'gzip 'gzip")).isEqualTo("#{C49B0062}");
    }

    @Test
    @DisplayName("A position counts what lies between, whatever its width")
    void measuresAPosition() {
        assertThat(answerTo("""
                s: {ěšč}
                decompress compress/part s 'gzip skip s 2 'gzip""")).isEqualTo("#{C49BC5A1}");
    }

    @Test
    @DisplayName("CHECKSUM reads a part the same way")
    void checksumReadsTheSameWay() {
        assertThat(answerTo("""
                reduce [
                    (checksum/part {ěšč} 'sha1 1) = checksum #{C49B} 'sha1
                    (checksum/part skip {ěšč} 1 'sha1 1) = checksum #{C5A1} 'sha1
                    (checksum/part skip {ěšč} 2 'sha1 -1) = checksum #{C48D} 'sha1
                ]""")).isEqualTo("[#(true) #(true) #(true)]");
    }
}
