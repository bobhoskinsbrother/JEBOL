package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What percent escaping answers, and which characters it lets through.
 *
 * <p>{@code n-strings.c}. Both ENHEX and DEHEX end on
 * {@code Set_Series(VAL_TYPE(arg), D_RET, ser)}, so the answer wears the
 * argument's datatype: bytes in, bytes out. The encoding is a transformation
 * of a series rather than a way of displaying one, and a caller who handed
 * over a binary is still working in bytes afterwards.
 *
 * <p>Rebol's own quoted-printable encoder is that caller, and it is how the
 * difference was found. It enhexes a binary and then PARSEs the answer to fold
 * long lines, inserting {@code #{3D0D0A}} at each break; given a string
 * instead, those three bytes went in as the six letters of their hex and every
 * wrapped line read {@code abc3D0D0Ade}.
 *
 * <p>Two rules about what is escaped, and they look like one rule until you
 * read the C twice. Escaping <em>text</em>, {@code c >= 0x80} is tested before
 * the unescaped set is consulted at all -- a character needing several bytes
 * of UTF-8 cannot go literally into an ASCII target however permissive the set
 * is. Escaping <em>bytes</em> there is no such test and the set alone decides;
 * a byte above ASCII is escaped only because every set the catalogue carries
 * is a hundred and twenty-eight bits wide. Hand a wider set and those bytes
 * pass through, which a real 3.22.5 confirms.
 *
 * <p>Every expectation here was read off a real 3.22.5 before it was written.
 */
class PercentEscapingFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("raised: try [" + source + "] raised/id");
    }

    @Test
    @DisplayName("enhex answers the datatype it was given")
    void enhexAnswersTheDatatypeItWasGiven() {
        assertThat(answerTo("""
                reduce [
                    type? enhex "a b"      type? enhex #{612062}
                    type? enhex %a-b       type? enhex http://x/a
                    type? enhex a@b.c      type? enhex <a b>
                ]"""))
                .isEqualTo("[#(string!) #(binary!) #(file!) #(url!) #(email!) #(tag!)]");
    }

    @Test
    @DisplayName("dehex answers the datatype it was given")
    void dehexAnswersTheDatatypeItWasGiven() {
        assertThat(answerTo("""
                reduce [
                    type? dehex "a%20b"    type? dehex #{6125323062}
                    type? dehex %a-b       type? dehex http://x/a
                    type? dehex a@b.c      type? dehex <a%20b>
                ]"""))
                .isEqualTo("[#(string!) #(binary!) #(file!) #(url!) #(email!) #(tag!)]");
    }

    @Test
    @DisplayName("a binary escapes to a binary, not to the text of one")
    void aBinaryEscapesToABinary() {
        assertThat(answerTo("enhex #{612062}")).isEqualTo("#{6125323062}");
    }

    @Test
    @DisplayName("an empty binary escapes to an empty binary")
    void anEmptyBinaryEscapesToAnEmptyBinary() {
        assertThat(answerTo("reduce [enhex #{} type? enhex #{}]"))
                .isEqualTo("[#{} #(binary!)]");
    }

    @Test
    @DisplayName("an issue is not a string and is refused")
    void anIssueIsRefused() {
        assertThat(errorIdFrom("enhex #ab")).isEqualTo("expect-arg");
    }

    @Test
    @DisplayName("a character above ASCII is escaped in a string")
    void aCharacterAboveAsciiIsEscapedInAString() {
        assertThat(answerTo("""
                enhex "Holčička\"""")).isEqualTo("\"Hol%C4%8Di%C4%8Dka\"");
    }

    @Test
    @DisplayName("and a byte above ASCII is escaped in a binary")
    void aByteAboveAsciiIsEscapedInABinary() {
        assertThat(answerTo("""
                enhex to binary! "Holčička\""""))
                .isEqualTo("#{486F6C254334253844692543342538446B61}");
    }

    @Test
    @DisplayName("but a set wide enough to hold those bytes lets them through")
    void anUnescapedSetWideEnoughLetsHighBytesThrough() {
        assertThat(answerTo("""
                enhex/except to binary! "Holčička" charset [#"^(00)" - #"^(FF)"]"""))
                .isEqualTo("#{486F6CC48D69C48D6B61}");
    }

    @Test
    @DisplayName("the quoted-printable set is ASCII without the equals sign")
    void theQuotedPrintableSetIsAsciiWithoutTheEqualsSign() {
        assertThat(answerTo("""
                b: select system/catalog/bitsets 'quoted-printable
                reduce [
                    true? find b #"a"          true? find b #"="
                    true? find b to char! 127  true? find b to char! 128
                ]"""))
                .isEqualTo("[#(true) #(false) #(true) #(false)]");
    }

    @Test
    @DisplayName("and it molds as the sixteen bytes the boot declares")
    void theQuotedPrintableSetMoldsAsTheBootDeclaresIt() {
        assertThat(answerTo("""
                mold select system/catalog/bitsets 'quoted-printable"""))
                .isEqualTo("\"#(bitset! #{FFFFFFFFFFFFFFFBFFFFFFFFFFFFFFFF})\"");
    }

    @Test
    @DisplayName("the shape Rebol's own quoted-printable encoder asks for")
    void theShapeTheQuotedPrintableCodecUses() {
        assertThat(answerTo("""
                enhex/escape/except to binary! "Holčička" #"="
                    select system/catalog/bitsets 'quoted-printable"""))
                .isEqualTo("#{486F6C3D43343D3844693D43343D38446B61}");
    }
}
