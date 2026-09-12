package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnbasePartCountsCharactersFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a count of characters takes all their bytes, not that many bytes")
    void aCountOfCharactersTakesAllTheirBytes() {
        assertThat(answerTo("""
                enbase/part "Doručené" 64 8""")).isEqualTo("\"RG9ydcSNZW7DqQ==\"");
    }

    @Test
    @DisplayName("and a count never stops halfway through a character")
    void aCountNeverStopsHalfwayThroughACharacter() {
        assertThat(answerTo("""
                enbase/part "Doručené" 64 5""")).isEqualTo("\"RG9ydcSN\"");
    }

    @Test
    @DisplayName("a position bound counts the same way a number does")
    void aPositionBoundCountsTheSameWay() {
        assertThat(answerTo("""
                s: "Doručené"
                reduce [enbase/part s 64 tail s  enbase/part s 64 skip s 5]"""))
                .isEqualTo("[\"RG9ydcSNZW7DqQ==\" \"RG9ydcSN\"]");
    }

    @Test
    @DisplayName("an ASCII string is unaffected, where a byte and a character agree")
    void anAsciiStringIsUnaffected() {
        assertThat(answerTo("""
                s: "abcd"
                reduce [enbase/part s 64 2  enbase/part s 64 skip s 2]"""))
                .isEqualTo("[\"YWI=\" \"YWI=\"]");
    }

    @Test
    @DisplayName("a binary counts bytes, because bytes are its units")
    void aBinaryCountsBytes() {
        assertThat(answerTo("""
                b: #{01020304}
                reduce [enbase/part b 64 2  enbase/part b 64 skip b 2]"""))
                .isEqualTo("[\"AQI=\" \"AQI=\"]");
    }

    @Test
    @DisplayName("a character outside the basic plane still counts as one")
    void aCharacterOutsideTheBasicPlaneCountsAsOne() {
        assertThat(answerTo("""
                s: rejoin [#"^(1F382)" "Katerina"]
                reduce [
                    length? s
                    enbase/part s 64 1
                    enbase/part s 64 3
                    enbase/part s 64 9
                ]"""))
                .isEqualTo("[9 \"8J+Ogg==\" \"8J+Ogkth\" \"8J+OgkthdGVyaW5h\"]");
    }

    @Test
    @DisplayName("the header Rebol's own MIME encoder builds")
    void theHeaderRebolsMimeEncoderBuilds() {
        assertThat(answerTo("""
                try [import 'mime-field]
                x: decode 'mime-field "=?UTF-8?Q?Doru=C4=8Den=C3=A9?="
                y: encode 'mime-field :x
                reduce [y  x = decode 'mime-field :y]"""))
                .isEqualTo("[\"=?UTF-8?B?RG9ydcSNZW7DqQ==?=\" #(true)]");
    }
}
