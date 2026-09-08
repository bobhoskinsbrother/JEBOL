package org.jebol.domain.parse;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What PARSE's INSERT and CHANGE put in, which the series decides.
 *
 * <p>Both reach the C's own INSERT through {@code Modify_String} and
 * {@code Modify_Block}, so they follow the rule INSERT follows everywhere
 * else: a value laid into a binary contributes bytes, and the same value laid
 * into a string contributes the text it FORMs to. {@code #{FFFF}} is two bytes
 * in one and four letters in the other, and both answers are right for their
 * series.
 *
 * <p>Formed into text either way -- which is what this did -- a binary went in
 * as the letters of its hex. Rebol's own quoted-printable encoder folds long
 * lines with {@code insert #{3D0D0A}} inside a PARSE over a binary, and every
 * wrapped line came out reading {@code abc3D0D0Ade} instead of carrying a soft
 * line break.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written.
 */
class ParseInsertsIntoBytesFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a binary inserted into a binary goes in as its bytes")
    void aBinaryInsertedIntoABinaryGoesInAsItsBytes() {
        assertThat(answerTo("""
                b: copy #{01020304}
                parse b [skip insert #{FFFF} to end]
                b""")).isEqualTo("#{01FFFF020304}");
    }

    @Test
    @DisplayName("a character goes in as its UTF-8")
    void aCharacterGoesInAsItsUtf8() {
        assertThat(answerTo("""
                b: copy #{01020304}
                parse b [skip insert #"^(FF)" to end]
                b""")).isEqualTo("#{01C3BF020304}");
    }

    @Test
    @DisplayName("an ASCII character is the one byte it always was")
    void anAsciiCharacterIsOneByte() {
        assertThat(answerTo("""
                b: copy #{01020304}
                parse b [skip insert #"A" to end]
                b""")).isEqualTo("#{0141020304}");
    }

    @Test
    @DisplayName("a whole number goes in as the single byte it names")
    void aWholeNumberGoesInAsOneByte() {
        assertThat(answerTo("""
                b: copy #{01020304}
                parse b [skip insert 255 to end]
                b""")).isEqualTo("#{01FF020304}");
    }

    @Test
    @DisplayName("a string goes in as its UTF-8")
    void aStringGoesInAsItsUtf8() {
        assertThat(answerTo("""
                b: copy #{01020304}
                parse b [skip insert "AB" to end]
                b""")).isEqualTo("#{014142020304}");
    }

    @Test
    @DisplayName("a paren is run first and its answer follows the same rule")
    void aParenIsRunFirstAndFollowsTheSameRule() {
        assertThat(answerTo("""
                b: copy #{01020304}
                parse b [skip insert (#{FFFF}) to end]
                b""")).isEqualTo("#{01FFFF020304}");
    }

    @Test
    @DisplayName("into a string the same values contribute the text they form to")
    void intoAStringTheValuesContributeTheirText() {
        assertThat(answerTo("""
                s: copy "abcd"
                parse s [skip insert #{FFFF} to end]
                t: copy "abcd"
                parse t [skip insert 255 to end]
                reduce [s t]""")).isEqualTo("[\"aFFFFbcd\" \"a255bcd\"]");
    }

    @Test
    @DisplayName("CHANGE follows the rule too, in a binary")
    void changeFollowsTheRuleInABinary() {
        assertThat(answerTo("""
                b: copy #{01020304}
                parse b [change skip #{FFFF} to end]
                c: copy #{01020304}
                parse c [change skip 255 to end]
                reduce [b c]""")).isEqualTo("[#{FFFF020304} #{FF020304}]");
    }

    @Test
    @DisplayName("and in a string, where a binary is its hex")
    void changeFollowsTheRuleInAString() {
        assertThat(answerTo("""
                s: copy "abcd"
                parse s [change skip #{FFFF} to end]
                t: copy "abcd"
                parse t [change skip 255 to end]
                reduce [s t]""")).isEqualTo("[\"FFFFbcd\" \"255bcd\"]");
    }

    @Test
    @DisplayName("the line folding Rebol's own quoted-printable encoder does")
    void theLineFoldingTheQuotedPrintableEncoderDoes() {
        assertThat(answerTo("""
                out: copy #{6162636465}
                parse out [any [3 skip [end | 1 skip end | insert #{3D0D0A}]]]
                out""")).isEqualTo("#{6162633D0D0A6465}");
    }
}
