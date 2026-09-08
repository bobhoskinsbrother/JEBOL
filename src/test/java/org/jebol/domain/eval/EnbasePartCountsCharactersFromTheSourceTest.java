package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What ENBASE's /PART counts, which is the series' own units.
 *
 * <p>{@code Partial1} answers a length in the units the series is made of, so
 * a string is bounded in characters and the UTF-8 encoding happens after. It
 * reads as a distinction without a difference until a character takes more
 * than one byte: eight characters of Czech are ten bytes, so counting bytes
 * takes too little, and a count that lands mid-character encodes a lead byte
 * with nothing following it.
 *
 * <p>Rebol's own MIME header encoder is where that showed. It cuts its input
 * into runs of at most seventeen characters to stay under the line limit --
 * {@code s: 1 17 skip e: (... enbase/part s 64 e ...)} -- so every accented
 * subject line lost its last letter, and a run ending on a two-byte character
 * broke it in half.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written.
 */
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

    /**
     * A character outside the basic plane is one character and two of Java's
     * own units, so a count that measures those takes half of it -- and half a
     * surrogate pair is not a character at all, which is why the answer was a
     * question mark rather than a short one. LENGTH? already said nine here;
     * the count ENBASE used did not agree with it.
     */
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
