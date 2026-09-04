package org.jebol.domain.value;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MOLD escapes a control character inside the braced form, read from
 * {@code mold-test.r3} "mold string with null char": a string holding a
 * quote molds with braces, and a null inside those braces is still {@code ^@}
 * rather than a raw byte.
 */
class MoldStringEscapingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a null molds as ^@ inside the quoted form")
    void aNullMoldsInTheQuotedForm() {
        assertThat(answerTo("""
                {"^^@a"} == mold {^@a}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a null molds as ^@ inside the braced form a quote forces")
    void aNullMoldsInTheBracedForm() {
        assertThat(answerTo("""
                {{^^@"}} == mold {^@"}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a bare control character inside braces is escaped, not written raw")
    void aBareControlCharacterInBracesIsEscaped() {
        assertThat(answerTo("""
                (mold rejoin [{a"} to char! 1 {b}]) = {{a"^^Ab}}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a string with unbalanced braces escapes them but keeps the quote")
    void unbalancedBracesAreEscaped() {
        assertThat(answerTo("""
                {{"a^^}^^{bc"}} = mold mold "a}{bc\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a character above the control range molds as itself, not hex")
    void aHighCharacterMoldsAsItself() {
        assertThat(answerTo("""
                {"é"} = mold "é\"""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("three or more newlines force the braced form, and stay literal newlines")
    void threeNewlinesForceBraces() {
        assertThat(answerTo("""
                v: mold "a^/b^/c^/d"
                reduce [v/1 = #"{"  (find v {^^/}) = none]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("two newlines stay in the quoted form as ^/")
    void twoNewlinesStayQuoted() {
        assertThat(answerTo("""
                v: mold "a^/b^/c"
                reduce [v/1 = #"^""  not none? find v {^^/}]""")).isEqualTo("[#(true) #(true)]");
    }

    @Test
    @DisplayName("a quote forces braces, where the quote is literal")
    void aQuoteForcesBraces() {
        assertThat(answerTo("""
                {{say "hi"}} = mold {say "hi"}""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a control char above 0x7F molds as ^(HH)")
    void aHighControlCharMoldsAsHex() {
        assertThat(answerTo("""
                8 = length? mold to char! 130""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a file escapes a space, a delimiter and a control char as %XX")
    void aFileEscapesSpecialCharacters() {
        assertThat(answerTo("""
                (mold to file! {a b}) = {%a%20b}""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                (load mold to file! {a[b}) = to file! {a[b}""")).isEqualTo("#(true)");
        assertThat(answerTo("""
                (load mold to file! rejoin [{a} to char! 1 {b}])
                        = to file! rejoin [{a} to char! 1 {b}]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a plain file round-trips through mold and load")
    void aPlainFileRoundTrips() {
        assertThat(answerTo("""
                (load mold %some/path.txt) = %some/path.txt""")).isEqualTo("#(true)");
    }
}
