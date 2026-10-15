package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CryptPortBlocksFromTheSourceTest {

    private static final String KEY = "#{2B7E151628AED2A6ABF7158809CF4F3C}";

    private static final String VECTOR = "#{000102030405060708090A0B0C0D0E0F}";

    private static final String FORTY_EIGHT_BYTES =
            "#{6BC1BEE22E409F96E93D7E117393172A"
            + "AE2D8A571E03AC9C9EB76FAC45AF8E51"
            + "30C81C46A35CE411E5FBC1191A0A52EF}";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String codebook() {
        return """
                whole: %s
                p: open make port! [scheme: 'crypt algorithm: 'AES-128-ECB key: %s]
                """.formatted(FORTY_EIGHT_BYTES, KEY);
    }

    private static String hexOf(String expression) {
        return "either binary? r: " + expression + " [enbase/flat r 16] [r]";
    }

    @Test
    @DisplayName("the fixture is three whole blocks")
    void theFixtureIsThreeWholeBlocks() {
        assertThat(answerTo("length? " + FORTY_EIGHT_BYTES)).isEqualTo("48");
    }

    @Test
    @DisplayName("nothing is ready until a whole block has arrived")
    void nothingIsReadyUntilAWholeBlockArrives() {
        for (int howMany : new int[] {0, 1, 15}) {
            assertThat(answerTo(codebook()
                    + "write p copy/part whole " + howMany + "\nread p"))
                    .as("after " + howMany + " bytes").isEqualTo("_");
        }
        assertThat(answerTo(codebook() + "read p")).isEqualTo("_");
    }

    @Test
    @DisplayName("a whole block comes out as soon as it is whole, and no more")
    void aWholeBlockComesOutAtOnce() {
        for (int howMany : new int[] {16, 17, 31}) {
            assertThat(answerTo(codebook()
                    + "write p copy/part whole " + howMany + "\n"
                    + hexOf("read p")))
                    .as("after " + howMany + " bytes")
                    .isEqualTo("\"3AD77BB40D7A3660A89ECAF32466EF97\"");
        }
    }

    @Test
    @DisplayName("and two blocks come out together once both are whole")
    void twoBlocksComeOutTogether() {
        for (int howMany : new int[] {32, 33}) {
            assertThat(answerTo(codebook()
                    + "write p copy/part whole " + howMany + "\n"
                    + hexOf("read p")))
                    .as("after " + howMany + " bytes")
                    .isEqualTo("""
                            {3AD77BB40D7A3660A89ECAF32466EF97F5D3D58503B9699DE785895A96FDBAAF}""");
        }
    }

    @Test
    @DisplayName("how the bytes are cut up does not change the answer")
    void howTheBytesAreCutUpDoesNotChangeTheAnswer() {
        for (int firstWrite : new int[] {1, 8, 15}) {
            assertThat(answerTo(codebook()
                    + "write p copy/part whole " + firstWrite + "\n"
                    + "write p copy/part skip whole " + firstWrite
                    + " " + (16 - firstWrite) + "\n"
                    + hexOf("read p")))
                    .as("cut after " + firstWrite)
                    .isEqualTo("\"3AD77BB40D7A3660A89ECAF32466EF97\"");
        }
    }

    @Test
    @DisplayName("reading twice leaves nothing the second time")
    void readingTwiceLeavesNothingTheSecondTime() {
        assertThat(answerTo(codebook()
                + "write p copy/part whole 16\nread p\nread p"))
                .isEqualTo("_");
    }

    @Test
    @DisplayName("update pads a partial block with noughts")
    void updatePadsThePartialBlockWithNoughts() {
        assertThat(answerTo(codebook()
                + "write p copy/part whole 1\n" + hexOf("read update p")))
                .isEqualTo("\"DE7676F065818C2070D758ABE6870EB6\"");
        assertThat(answerTo(codebook()
                + "write p copy/part whole 15\n" + hexOf("read update p")))
                .isEqualTo("\"5F7D4099C9D261848BE41DF98A4F8021\"");
    }

    @Test
    @DisplayName("update with nothing held back does nothing at all")
    void updateWithNothingHeldDoesNothing() {
        assertThat(answerTo(codebook() + hexOf("read update p")))
                .isEqualTo("_");
        assertThat(answerTo(codebook()
                + "write p copy/part whole 16\n" + hexOf("read update p")))
                .isEqualTo("\"3AD77BB40D7A3660A89ECAF32466EF97\"");
        assertThat(answerTo(codebook()
                + "write p copy/part whole 1\nread update p\n"
                + hexOf("read update p"))).isEqualTo("_");
    }

    @Test
    @DisplayName("taking is updating and then reading")
    void takingIsUpdatingAndThenReading() {
        assertThat(answerTo(codebook()
                + "write p copy/part whole 1\n" + hexOf("take p")))
                .isEqualTo("\"DE7676F065818C2070D758ABE6870EB6\"");
        assertThat(answerTo(codebook() + hexOf("take p"))).isEqualTo("_");
        assertThat(answerTo(codebook()
                + "write p copy/part whole 17\nread p\n" + hexOf("take p")))
                .isEqualTo("\"8EE6979FDE06A0590BB075BE7F125F5C\"");
    }

    @Test
    @DisplayName("the padding is noughts and comes back as noughts")
    void thePaddingIsNoughtsAndComesBackAsNoughts() {
        assertThat(answerTo("""
                c: open make port! [
                    scheme: 'crypt algorithm: 'AES-128-CBC key: %s init-vector: %s
                ]
                write c #{6BC1BEE22E409F96}
                crypted: take c
                d: open make port! [
                    scheme: 'crypt algorithm: 'AES-128-CBC direction: 'decrypt
                    key: %s init-vector: %s
                ]
                write d :crypted
                read d""".formatted(KEY, VECTOR, KEY, VECTOR)))
                .isEqualTo("#{6BC1BEE22E409F960000000000000000}");
    }

    @Test
    @DisplayName("chaining carries between writes until the vector is set again")
    void chainingCarriesBetweenWritesUntilTheVectorIsSetAgain() {
        String chaining = """
                p: open make port! [
                    scheme: 'crypt algorithm: 'AES-128-CBC key: %s init-vector: %s
                ]
                write p #{6BC1BEE22E409F96E93D7E117393172A}
                first-block: enbase/flat read p 16
                """.formatted(KEY, VECTOR);
        assertThat(answerTo(chaining + """
                write p #{AE2D8A571E03AC9C9EB76FAC45AF8E51}
                reduce [first-block enbase/flat read p 16]"""))
                .isEqualTo("""
                        ["7649ABAC8119B246CEE98E9B12E9197D" "5086CB9B507219EE95DB113A917678B2"]""");
        assertThat(answerTo(chaining + """
                modify p 'init-vector %s
                write p #{AE2D8A571E03AC9C9EB76FAC45AF8E51}
                enbase/flat read p 16""".formatted(VECTOR)))
                .isEqualTo("\"BB4428E13712722750D4DBEC8294BBA0\"");
    }
}
