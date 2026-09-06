package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The eight Brotli levels that search the input properly, and the bytes they
 * write.
 *
 * <p>Levels zero and one read the input in fixed pieces and take whatever
 * matches they trip over. Two and up keep a window of everything seen so far,
 * look one byte ahead before committing to a match, consult the hundred and
 * twenty thousand bytes of dictionary every decoder carries, and then divide
 * the result into stretches with a prefix code apiece. Each of the eight makes
 * different trades inside that, so each writes different bytes, and every
 * expectation below was measured on {@code ./r3-head} rather than on this
 * build.
 *
 * <p>Round-tripping proves almost nothing here and the assertions are on bytes
 * for that reason. Any number of encoders read back correctly; the property
 * worth having is that a blob written here matches one written by a real
 * 3.22.5, which is what a checksum over a compressed file or a fixture checked
 * into a repository actually depends on.
 *
 * <p>The lengths tested are not arbitrary. The encoder reads the input in
 * blocks -- sixteen kilobytes at levels two and three, sixty four at four to
 * eight, two hundred and fifty six at nine -- and the first defect found in
 * this port only appeared in the second block, because the table of where runs
 * were last seen was being cleared at every block instead of once. A megabyte
 * is the other boundary: above it the encoder swaps to a wider hash and offers
 * itself a thirteen-way division of literals by what came before them, so the
 * two sides of that line take different paths through the code.
 */
class BrotliLevelsTwoToNineFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /**
     * Two shapes of test data and a short checksum, so that a megabyte of
     * output can be asserted on in one line.
     *
     * <p>MIXTURE alternates twenty bytes of hash with a phrase, which gives
     * data that has matches in it worth finding and no pattern beyond them --
     * the shape that makes the search do work. REPEATING is one short phrase
     * over and over, where every position matches and the copies are as long as
     * the encoder will let them be.
     *
     * <p>Both build their bytes twenty or more at a time rather than one at a
     * time, which is not only for speed. A first attempt built each byte with
     * {@code to char!}, and every value above a hundred and twenty seven went
     * in as two bytes of UTF-8 rather than one: a request for sixteen thousand
     * bytes gave forty-eight thousand, so the lengths the tests named were not
     * the lengths they used and the block boundary they meant to sit on was
     * nowhere near.
     */
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
            sha: func [x][trim/all enbase checksum x 'sha1 64]
            """;

    private static final String SENTENCE =
            "sentence: to binary! {The quick brown fox jumps over the lazy "
                    + "dog. The quick brown fox jumps again.}\n";

    @Nested
    @DisplayName("each of the eight levels, byte for byte")
    class EachLevel {

        /**
         * The whole point of eight levels rather than one, on a sentence short
         * enough to write the answers out in full.
         */
        @Test
        @DisplayName("a sentence at levels two, three and four")
        void aSentenceAtTheLowerLevels() {
            assertThat(answerTo(SENTENCE + """
                    reduce [
                        (compress/level sentence 'br 2) = #{
                            1B4C000080AAAAAAEAFF74A5C3496F273D5C44CD44173335
                            15333553DB06E0602C88C42431C09D364D963E2BC728D34A
                            E8F634A2BD2C63DB4FECD6CE95952AFFC76CC9A1832692A4
                            3407}
                        (compress/level sentence 'br 3) = #{
                            1B4C00000042B695EA85444B843260023E4C51FC4A16B2E2
                            BAF44539468D0BA1DBD398EC651EDB7E62B776AEA2ACF27F
                            24CB0E40B2D4E600}
                        (compress/level sentence 'br 4) = #{
                            1B4C0000447543652BD49C429811C6CB1981FC364516A2A0
                            9217BA62A22007FFC20E3D0D5E2FF2AA7D42B78C07C660E1
                            FFE034108038706A04}
                    ]""")).isEqualTo("[#(true) #(true) #(true)]");
        }

        /**
         * Five to nine agree on a sentence this short, because what separates
         * them is how far back through the candidate matches they walk and a
         * sentence gives them all the same few.
         */
        @Test
        @DisplayName("and at five to nine, which agree on input this short")
        void aSentenceAtTheHigherLevels() {
            assertThat(answerTo(SENTENCE + """
                    expected: #{
                        1B4C000044DB46A92EA46BC950144F459E89F2C3440E1CB24413D
                        2CFDF57A6AD3249E1FCC20D9E86C08BBC6A9FE0D6F1C03858EC7F
                        F08C02800405}
                    collect [
                        repeat n 5 [
                            keep (compress/level sentence 'br n + 4) = expected
                        ]
                    ]""")).isEqualTo("[#(true) #(true) #(true) #(true) #(true)]");
        }

        /**
         * A paragraph is long enough to tell all eight apart but three ways,
         * which is what stops the assertions above passing for an encoder that
         * quietly does the same thing at every level.
         */
        @Test
        @DisplayName("a paragraph, where the eight settle on three answers")
        void aParagraphTellsTheLevelsApart() {
            assertThat(answerTo(PARAGRAPH + """
                    collect [
                        foreach n [2 3 4 5 6 7 8 9][
                            keep length? compress/level para 'br n
                        ]
                    ]""")).isEqualTo("[99 86 89 88 88 89 89 89]");
        }

        @Test
        @DisplayName("and the bytes of the paragraph at level six")
        void theParagraphAtTheDefaultLevel() {
            assertThat(answerTo(PARAGRAPH + """
                    (compress/level para 'br 6) = #{
                        1BA90000C47EE74FEBB5CBB80B0F85937B3F7D9D1CB85D7F3E60
                        1850F3E6CD13CB2D4FD954192F5F3775C32A7DE4689283067D28
                        E03D2B3C5E5BA9E14AC31B2A5C7C30720EF55D751CF530F7106A
                        EB1BE6A3A75DE583FD8F}""")).isEqualTo("#(true)");
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

        /**
         * Under three bytes there is nothing a prefix code could pay for, so
         * they are stored as they stand. Three is the first length the encoder
         * will consider compressing, and on data this short it still declines.
         */
        @Test
        @DisplayName("nothing, one, two, three, four and five bytes")
        void theShortestLengths() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach n [0 1 2 3 4 5][
                            keep compress/level (mixture n) 'br 6
                        ]
                    ]""")).isEqualTo("[#{3B} #{0B00809203} #{8B0080927103} "
                    + "#{0B018092713D03} #{8B018092713D4703} "
                    + "#{0B028092713D470903}]");
        }
    }

    @Nested
    @DisplayName("lengths either side of one block of input")
    class AroundABlockBoundary {

        /**
         * One byte under, exactly one block, and one byte over, at a level that
         * reads sixteen kilobyte blocks and a level that reads sixty four. The
         * one-over cases are the ones that were wrong: a hash table cleared
         * again at the start of the second block loses every position the first
         * block put in it, and the encoder then fails to find matches it should
         * have found. Everything up to the end of the first block was already
         * exact when that defect was present.
         */
        @Test
        @DisplayName("sixteen kilobytes, at levels two and six")
        void aroundSixteenKilobytes() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach n [16383 16384 16385][
                            foreach q [2 6][
                                keep sha compress/level (mixture n) 'br q
                            ]
                        ]
                    ]""")).isEqualTo("""
                    ["HLY8fUpM8ahUG3DEafY9mS5abDI=" "z9Ki0tsLwl1K6RDoamC2QIVfxg4=" \
                    "Gt8s7zjywJupZ5AZwQwL8xBsZd8=" "3tijAc0WUBuqCsAY5Itw0eT0V5k=" \
                    "6S1lIH0MSOmx2KgS0pwWPs654Pg=" "Wr44jlSmmco63V3MdMGc/AoaNa0="]""");
        }

        @Test
        @DisplayName("sixty four kilobytes, at levels two and six")
        void aroundSixtyFourKilobytes() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach n [65535 65536 65537][
                            foreach q [2 6][
                                keep sha compress/level (mixture n) 'br q
                            ]
                        ]
                    ]""")).isEqualTo("""
                    ["aPQYfql0YuNlzMo/LEmUL75BYhk=" "sEMIa37XgikRmsjt53v740dRZIA=" \
                    "2SVYEIyN7xBkbHkvtuGCVqDLAVY=" "j39Vtt7qiwvy7HFFjeRKg2hZFqU=" \
                    "xUCq/4zWejZySBC0K+OhPRVy478=" "ptPjBij5AioSLU2Da6EykXjd2L0="]""");
        }

        /**
         * The same boundaries on data that is one short phrase over and over,
         * where every position matches and the copies run the length of the
         * block. That exercises the other half of the block join: a copy that
         * reaches the end of one block and carries on into the next is grown
         * rather than started again.
         */
        @Test
        @DisplayName("and on data that repeats, where copies cross the join")
        void aroundABlockOnRepeatingData() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach n [16384 16385 65536 65537][
                            foreach q [2 6][
                                keep sha compress/level (repeating n) 'br q
                            ]
                        ]
                    ]""")).isEqualTo("""
                    ["sKybDkbCjxqiiKmDYeHHnkAOFFQ=" "LUaTXEBXGRjBKRJn+mvujRn5j8M=" \
                    "4WzEf76Ng7PW4JADUzgwUOCTECI=" "t0u3+khhAo3UsqTsrvnQefSQt2U=" \
                    "IhRKKZvvk2Sj5NByKCF84Og7M/g=" "DkQf+VhPz5YUNEmE4sGzXqQoOuk=" \
                    "THVj2HvG0XCWBfwhCoabUZqa02A=" "I7GJrr4Ppw3bWJoXUB+7kb1dnkQ="]""");
        }

        @Test
        @DisplayName("and two whole blocks of the wider kind")
        void twoWholeBlocks() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach q [2 6][
                            keep sha compress/level (mixture 131072) 'br q
                        ]
                    ]""")).isEqualTo("""
                    ["JC5TR6YrTrU5PJpef3ll5kjBMTI=" "tQhD2rANPmC+LfN7wiF6Wqg/Cu8="]""");
        }
    }

    @Nested
    @DisplayName("either side of the megabyte where the search changes")
    class AroundAMegabyte {

        /**
         * A byte under a megabyte and exactly a megabyte. Above the line the
         * encoder is told how much input is coming and takes a different route
         * for it: level four swaps its table of recent positions for one eight
         * times larger that no longer consults the dictionary, and five to nine
         * swap theirs for one that hashes five bytes rather than four and
         * refuses a candidate outright unless its first four bytes match.
         */
        @Test
        @DisplayName("a byte under, at levels four, six and nine")
        void justUnderAMegabyte() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach q [4 6 9][
                            keep sha compress/level (mixture 1048575) 'br q
                        ]
                    ]""")).isEqualTo("""
                    ["EJAgZI3Uz16UgmuT74lwN6iHTmo=" "uN5xqpes29VG8UROo/w223MXoWg=" \
                    "i5ouAUB6euZCe+U9n333AnRUikk="]""");
        }

        @Test
        @DisplayName("and exactly a megabyte, where the answers change")
        void exactlyAMegabyte() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    collect [
                        foreach q [4 6 9][
                            keep sha compress/level (mixture 1048576) 'br q
                        ]
                    ]""")).isEqualTo("""
                    ["xlBYav1tXeoFxNpEjI3dN8cZHGY=" "8B2gn2RGduJtzjsY6+mS8/EV2A8=" \
                    "BsEEh0fHaS3kra/JnPD2N/C2CjU="]""");
        }
    }

    @Nested
    @DisplayName("data the encoder gives up on")
    class WhereCompressingDoesNotPay {

        /**
         * Sixty bytes of ordinary words, found by comparing against a real
         * 3.22.5 on random inputs. Level two builds a code for it, the code
         * comes out five bytes longer than the words themselves, and the whole
         * attempt is thrown away and the bytes written as they stand.
         *
         * <p>Four bytes longer would have been kept. The encoder weighs the
         * input against the whole bytes of a buffer that starts part way
         * through a byte, so the leftover bits of the previous meta-block count
         * toward this one's size, and reading that as the length of the
         * compressed form alone gets the boundary wrong by one.
         */
        @Test
        @DisplayName("sixty bytes whose compressed form is longer than they are")
        void whereCompressingMakesItBigger() {
            assertThat(answerTo("""
                    words: to binary! {brown quick compression fox brown brown \
                    the the brown fox co}
                    reduce [
                        length? words
                        length? compress/level words 'br 2
                        (compress/level words 'br 2) = #{
                            8B1D8062726F776E20717569636B20636F6D7072657373696F
                            6E20666F782062726F776E2062726F776E207468652074686520
                            62726F776E20666F7820636F03}
                    ]""")).isEqualTo("[60 64 #(true)]");
        }

        /**
         * Fifty kilobytes with no pattern in them at all, made by hashing a
         * seed over and over so the same bytes can be built anywhere. Every
         * meta-block is stored rather than coded, all eight levels give the
         * same four bytes of overhead, and those are the bytes a real 3.22.5
         * writes.
         *
         * <p>The data has to be genuinely incompressible for this to be worth
         * anything. A first attempt used a linear congruential generator, whose
         * low byte repeats after a few hundred values -- fifty thousand of them
         * compressed to thirteen bytes, and the test would have passed while
         * measuring nothing.
         */
        @Test
        @DisplayName("fifty kilobytes of noise are stored, at every level")
        void noiseIsStored() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    noise: make binary! 50000
                    h: checksum "seed" 'sha1
                    while [(length? noise) < 50000][
                        append noise h
                        h: checksum h 'sha1
                    ]
                    noise: copy/part noise 50000
                    collect [
                        keep sha noise
                        foreach q [2 3 4 5 6 7 8 9][
                            keep length? compress/level noise 'br q
                            keep sha compress/level noise 'br q
                        ]
                    ]""")).isEqualTo("""
                    ["sexV8z81RXaOuHBPdUu3Zp10cQA=" \
                    50004 "0Ftn1cB3ict0/uqIYN1ZAUItrNE=" \
                    50004 "0Ftn1cB3ict0/uqIYN1ZAUItrNE=" \
                    50004 "0Ftn1cB3ict0/uqIYN1ZAUItrNE=" \
                    50004 "0Ftn1cB3ict0/uqIYN1ZAUItrNE=" \
                    50004 "0Ftn1cB3ict0/uqIYN1ZAUItrNE=" \
                    50004 "0Ftn1cB3ict0/uqIYN1ZAUItrNE=" \
                    50004 "0Ftn1cB3ict0/uqIYN1ZAUItrNE=" \
                    50004 "0Ftn1cB3ict0/uqIYN1ZAUItrNE="]""");
        }
    }

    @Nested
    @DisplayName("reading back what it wrote")
    class ReadingItBack {

        /**
         * The weaker property, kept because it catches a different mistake: a
         * stream that matches a real 3.22.5 byte for byte and yet cannot be
         * read by the decoder in this same build would mean the two halves have
         * drifted apart.
         */
        @Test
        @DisplayName("every level, on four shapes of data")
        void everyLevelOnFourShapes() {
            assertThat(answerTo(THE_DATA_AND_HOW_TO_MEASURE_IT + """
                    shapes: reduce [
                        mixture 0
                        mixture 3
                        repeating 70000
                        mixture 70000
                    ]
                    bad: copy []
                    foreach data shapes [
                        foreach q [2 3 4 5 6 7 8 9][
                            unless data = decompress (compress/level data 'br q) 'br [
                                append bad reduce [length? data q]
                            ]
                        ]
                    ]
                    bad""")).isEqualTo("[]");
        }
    }
}
