package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ENBASE of a whole number, which encodes the fewest bytes that hold it.
 *
 * <p>{@code enbase 0 16} is {@code "00"} and {@code enbase 256 16} is
 * {@code "0100"}: the leading nought bytes come off and one byte is always
 * left. Encoding all eight bytes of a long would bury a small number in
 * noughts.
 */
class EnbaseOfANumberFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("the leading nought bytes come off, and one byte is always left")
    void theLeadingNoughtsComeOff() {
        assertThat(answerTo("""
                collect [
                    foreach n [0 1 255 256 65535 65536 16777216][
                        keep enbase n 16
                    ]
                ]""")).isEqualTo(
                "[\"00\" \"01\" \"FF\" \"0100\" \"FFFF\" \"010000\" \"01000000\"]");
    }

    @Test
    @DisplayName("a negative keeps all eight, having no leading noughts to drop")
    void aNegativeKeepsEveryByte() {
        assertThat(answerTo("""
                enbase -1 16""")).isEqualTo("\"FFFFFFFFFFFFFFFF\"");
    }

    @Test
    @DisplayName("base two writes the same bytes as bits")
    void baseTwoWritesTheBits() {
        assertThat(answerTo("""
                reduce [enbase 0 2 enbase 2#00010111 2 enbase 256 2]"""))
                .isEqualTo("[\"00000000\" \"00010111\" \"0000000100000000\"]");
    }

    @Test
    @DisplayName("and base sixty-four pads to its own boundary")
    void baseSixtyFourPads() {
        assertThat(answerTo("""
                reduce [enbase 0 64 enbase 256 64]""")).isEqualTo("[\"AA==\" \"AQA=\"]");
    }
}
