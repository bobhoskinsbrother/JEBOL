package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
