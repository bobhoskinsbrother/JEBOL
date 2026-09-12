package org.jebol.domain.eval.brotli;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BrotliLevelsTenAndElevenFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard()
                .withWallClockLimit(Duration.ofMinutes(1)));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String THE_DATA_AND_HOW_TO_MEASURE_IT = """
            mixture: func [n [integer!] /local b h][
                b: make binary! n
                h: checksum "seed" 'sha1
                while [(length? b) < n][
                    append b h
                    append b "the quick brown fox jumps over the lazy dog "
                    h: checksum h 'sha1
                ]
                copy/part b n
            ]
            repeating: func [n [integer!] /local b][
                b: make binary! n
                while [(length? b) < n][append b "abcdefgh"]
                copy/part b n
            ]
            noise: func [n [integer!] /local b h][
                b: make binary! n
                h: checksum "noise" 'sha1
                while [(length? b) < n][append b h  h: checksum h 'sha1]
                copy/part b n
            ]
            sha: func [x][trim/all enbase checksum x 'sha1 64]
            """;

    @Nested
    @DisplayName("the bytes, at both levels")
    class TheBytes {

        @Test
        @DisplayName("a sentence at level ten")
        void aSentenceAtTen() {
            assertThat(answerTo("""
                    (compress/level "The quick brown fox jumps over the lazy dog. \
                    The quick brown fox jumps again." 'br 10) = #{
                        1B4C00A02C8EC7F9D7E2AD58BAC346A8F4A2424B3332B6067199
                        CB9B78677A15F6061B70E090ADC13E901EA0F4945A3A1B228913
                        A103DC7D2D9E89E8E7E475F404}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and at level eleven, which is a different answer")
        void aSentenceAtEleven() {
            assertThat(answerTo("""
                    (compress/level "The quick brown fox jumps over the lazy dog. \
                    The quick brown fox jumps again." 'br 11) = #{
                        1B4C00002C8EC7F9D7E2AD58BAC32A2AB42433636B1097B9BC79
                        86A757616FB001070E593AEC0BF1E0212BF13B45141F87D8C535
                        3D3A96AB7D8A00110C}""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a paragraph, at both levels")
        void aParagraph() {
            assertThat(answerTo(PARAGRAPH + """
                    reduce [
                        length? compress/level para 'br 10
                        (compress/level para 'br 10) = #{
                            1BA9004044B7797EBE4F1A0EAC4523249D9CE8810F826D2F0F
                            290812482C00CB6D0C87D32D380B222C2710FB3DD0621DF056
                            CD4B0DDD6343CE85874916943B94CA381C1945F70D794B3BFC
                            557E03}
                        length? compress/level para 'br 11
                        (compress/level para 'br 11) = #{
                            1BA9006044B7797EBE8F2A0AD6A211924E4EF460DCF6F280E2
                            8482208140E2DCC6708878DAFD8208CB05445E0FB85747B556
                            834BCEDCE7140D0B1140FBE67652E9079AB29CFB4669483BEC
                            B57F01}
                    ]""")).isEqualTo("[78 #(true) 78 #(true)]");
        }

        @Test
        @DisplayName("both beat every level below them on that paragraph")
        void bothBeatTheLevelsBelow() {
            assertThat(answerTo(PARAGRAPH + """
                    collect [
                        foreach q [6 9 10 11][
                            keep length? compress/level para 'br q
                        ]
                    ]""")).isEqualTo("[88 89 78 78]");
        }

        private static final String PARAGRAPH =
                "para: to binary! {It was the best of times, it was the worst "
                        + "of times, it was the age of wisdom, it was the age of "
                        + "foolishness, it was the epoch of belief, it was the "
                        + "epoch of incredulity.}\n";
    }

    @Nested
    @DisplayName("lengths at the bottom of the range")
    class TheShortestInputs {

        @Test
        @DisplayName("nothing to five bytes, which every level agrees on")
        void theShortestLengths() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach n [0 1 2 3 4 5][
                            keep compress/level (mixture n) 'br 11
                        ]
                    ]""")).isEqualTo("[#{3B} #{0B00809203} #{8B0080927103} "
                    + "#{0B018092713D03} #{8B018092713D4703} "
                    + "#{0B028092713D470903}]");
        }
    }

    @Nested
    @DisplayName("lengths either side of one block of input")
    class AroundABlockBoundary {

        @Test
        @DisplayName("a quarter of a megabyte, at both levels")
        void aroundAQuarterOfAMegabyte() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach n [262143 262144 262145][
                            foreach q [10 11][
                                keep sha compress/level (mixture n) 'br q
                            ]
                        ]
                    ]""")).isEqualTo("""
                    ["MiDkVvodcaFXqDvL1w+qwY0kgR0=" "3W4SR8Kz0bbAMOkHsm77HgxzkAk=" \
                    "VuVXR7LqsOmDKdmj6lrs5Ixjr+c=" "Ecqu5kjR++72Q/SOQtFf3eiKI/o=" \
                    "pTRuucp96rBS8UUOinyxBS5WJQU=" "17H1ARtrgeOmiKB9p/DlY1qOvTo="]""");
        }

        @Test
        @DisplayName("and two whole blocks")
        void twoWholeBlocks() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach q [10 11][
                            keep sha compress/level (mixture 524288) 'br q
                        ]
                    ]""")).isEqualTo("""
                    ["gzYiU0KCl3H/13AUQf/2UVP96Fo=" "pGc17C2fO2s4WK+2I1+ET/73p+c="]""");
        }
    }

    @Nested
    @DisplayName("three shapes of data")
    class ShapesOfData {

        @Test
        @DisplayName("text, repetition and noise, at both levels")
        void threeShapes() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach q [10 11][
                            keep sha compress/level (mixture 4346) 'br q
                            keep sha compress/level (repeating 70000) 'br q
                            keep sha compress/level (noise 50000) 'br q
                        ]
                    ]""")).isEqualTo("""
                    ["G3Qx51IJUZV0JtNU9spEruJcti8=" "pk4Z7aU6IKMszZ0wWm5BxS0KCa8=" \
                    "07OqdSty5QUTr/eNH7gWWM8WHDo=" "bMMGq3ot3TAwVgjLwVh6+lWe8eU=" \
                    "pk4Z7aU6IKMszZ0wWm5BxS0KCa8=" "07OqdSty5QUTr/eNH7gWWM8WHDo="]""");
        }
    }

    @Nested
    @DisplayName("a file from Brotli's own corpus")
    class SomebodyElsesData {

        @Test
        @DisplayName("thirty kilobytes of map data, at both levels")
        void mapData() throws Exception {
            byte[] fixture = java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of(
                            "src/test/resources/brotli/mapsdatazrh-first-30k.bin"));
            String asRebolBinary = asBraceless(fixture);
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT
                    + "data: debase {" + asRebolBinary + "} 64\n" + """
                    reduce [
                        length? data
                        sha data
                        length? compress/level data 'br 10
                        sha compress/level data 'br 10
                        length? compress/level data 'br 11
                        sha compress/level data 'br 11
                    ]"""))
                    .isEqualTo("""
                            [30000 "o1PgXOw1H1VpY+Z2ej868t3mPW8=" \
                            18797 "gpig4L4iWhBWPRKuxsIZ+4iy7nY=" \
                            18688 "ibgUGXcB8zJWQmjXmgubKuAihkc="]""");
        }

        private static String asBraceless(byte[] bytes) {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        }
    }

    @Nested
    @DisplayName("reading back what it wrote")
    class ReadingItBack {

        @Test
        @DisplayName("both levels, on four shapes of data")
        void bothLevelsOnFourShapes() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    shapes: reduce [
                        mixture 0
                        mixture 3
                        repeating 70000
                        mixture 262145
                        noise 50000
                    ]
                    bad: copy []
                    foreach data shapes [
                        foreach q [10 11][
                            unless data = decompress (compress/level data 'br q) 'br [
                                append bad reduce [length? data q]
                            ]
                        ]
                    ]
                    bad""")).isEqualTo("[]");
        }
    }
}
