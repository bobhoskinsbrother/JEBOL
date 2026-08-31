package org.jebol.domain.value;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * How MOLD lays out a long run of bytes, from {@code Mold_Binary} and
 * {@code Mold_Image_Data}.
 *
 * <p>Both break the digits into lines once there are enough of them, so a
 * binary long enough to matter arrives as an even block instead of one line
 * running off the screen. A binary takes thirty-two bytes to a line and an
 * image ten pixels, and each stays flat below its own threshold.
 *
 * <p>MOLD only. FORM writes the digits bare and unbroken, because FIND forms
 * its needle before looking for it and a newline through the middle would stop
 * it matching.
 *
 * <p>Asserted through REBOL rather than on the molded text itself, because the
 * answers are strings holding newlines and comparing them in Java would mean
 * escaping the very characters under test.
 */
class MoldedBytesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String BYTES = """
            bytes: func [n][to binary! head insert/dup copy #{} #{41} n]
            lines: func [text][split text newline]
            """;

    @Test
    @DisplayName("thirty-two bytes stay on one line")
    void thirtyTwoBytesStayOnOneLine() {
        assertThat(answerTo(BYTES + """
                not find mold bytes 32 newline""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("thirty-three break, into the brace, a full line and the rest")
    void thirtyThreeBytesBreak() {
        assertThat(answerTo(BYTES + """
                reduce [lines mold bytes 33]""")).isEqualTo(
                "[[\"#{\" {4141414141414141414141414141414141414141414141414141414141414141}"
                        + " \"41}\"]]");
    }

    @Test
    @DisplayName("an exact multiple leaves the closing brace alone on its line")
    void anExactMultipleClosesOnItsOwnLine() {
        assertThat(answerTo(BYTES + """
                last lines mold bytes 64""")).isEqualTo("\"}\"");
    }

    @Test
    @DisplayName("and a short last line keeps the brace beside it")
    void aShortLastLineKeepsTheBrace() {
        assertThat(answerTo(BYTES + """
                last lines mold bytes 65""")).isEqualTo("\"41}\"");
    }

    @Test
    @DisplayName("every full line is sixty-four digits, which is thirty-two bytes")
    void everyFullLineIsThirtyTwoBytes() {
        assertThat(answerTo(BYTES + """
                collect [
                    foreach line lines mold bytes 100 [keep length? line]
                ]""")).isEqualTo("[2 64 64 64 9]");
    }

    @Test
    @DisplayName("and the whole thing still reads back as the binary it came from")
    void itStillReadsBack() {
        assertThat(answerTo(BYTES + """
                (bytes 100) = load mold bytes 100""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("FORM writes the digits bare and never breaks them")
    void formNeverBreaks() {
        assertThat(answerTo(BYTES + """
                reduce [
                    not find form bytes 100 newline
                    not find form bytes 100 #"{"
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("MOLD/FLAT keeps the braces and drops the breaks")
    void moldFlatDropsTheBreaks() {
        assertThat(answerTo(BYTES + """
                reduce [
                    not find mold/flat bytes 100 newline
                    true? find mold/flat bytes 100 #"{"
                ]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("MOLD/PART cuts the bytes first, so a short answer stays on one line")
    void moldPartCutsTheBytesFirst() {
        assertThat(answerTo(BYTES + """
                mold/part bytes 40 8""")).isEqualTo("\"#{414141\"");
    }

    @Test
    @DisplayName("a bitset breaks its bytes the same way a binary does")
    void aBitsetBreaksTheSameWay() {
        assertThat(answerTo(BYTES + """
                collect [
                    foreach line lines mold charset [0 - 400] [keep length? line]
                ]""")).isEqualTo("[12 64 40]");
    }

    @Test
    @DisplayName("an image with no pixels writes empty braces")
    void anEmptyImageWritesEmptyBraces() {
        assertThat(answerTo("""
                mold make image! 0x0""")).isEqualTo("\"make image! [0x0 #{}]\"");
    }

    @Test
    @DisplayName("fewer than ten pixels stay on one line")
    void ninePixelsStayOnOneLine() {
        assertThat(answerTo("""
                not find mold make image! 9x1 newline""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("ten pixels break, sixty digits to a line, brace alone after")
    void tenPixelsBreak() {
        assertThat(answerTo(BYTES + """
                collect [
                    foreach line lines mold make image! 20x1 [keep length? line]
                ]""")).isEqualTo("[20 60 60 2]");
    }

    @Test
    @DisplayName("MOLD/FLAT flattens an image too")
    void moldFlatFlattensAnImage() {
        assertThat(answerTo("""
                not find mold/flat make image! 20x1 newline""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("MOLD/ALL writes an image in the construct form, not MAKE")
    void moldAllUsesTheConstructForm() {
        assertThat(answerTo("""
                mold/all make image! 2x1""")).isEqualTo(
                "\"#(image! 2x1 #{FFFFFFFFFFFF})\"");
    }

    @Test
    @DisplayName("an empty file molds with its quotes, since a bare percent is not one")
    void anEmptyFileKeepsItsQuotes() {
        assertThat(answerTo("""
                mold to file! {}""")).isEqualTo("""
                {%""}""");
    }
}
