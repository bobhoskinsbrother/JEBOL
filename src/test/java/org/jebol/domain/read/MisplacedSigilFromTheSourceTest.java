package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What may follow a sigil, from the three {@code Scan_Token} cases that decide it:
 * {@code LEX_SPECIAL_TICK}, {@code LEX_SPECIAL_COLON} and
 * {@code LEX_DELIMIT_SLASH}.
 *
 * <p>A sigil names a word. Each refusal below asks for a word that cannot exist --
 * one starting with a digit, one that is itself a sigil, one that is the none
 * literal, one already carrying a sigil at the other end -- and the C answers a
 * <em>negative</em> token for each, which is its way of saying syntax error. Every
 * one carries a comment in the source saying which spelling it is turning away:
 * {@code // no '2nd}, {@code // no ':X}, {@code // no ''foo},
 * {@code // no :'foo ::foo}.
 *
 * <p>JEBOL read all nine as perfectly good lit-words and get-words, and refused one
 * shape the C allows on purpose. Both directions matter: a reader that accepts too
 * much turns a typo into a definition, and one that accepts too little cannot read
 * what MOLD wrote.
 */
class MisplacedSigilFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    /** The id LOAD gives up for a source it will not read. */
    private static String errorIdFromLoading(String source) {
        return answerTo("e: try [load " + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a sigil cannot follow a sigil")
    class TwoSigils {

        @Test
        @DisplayName("a lit-word cannot name a get-word")
        void aTickThenAColon() {
            assertThat(errorIdFromLoading("""
                    {':a}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {':a:}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("nor another lit-word")
        void aTickThenATick() {
            assertThat(errorIdFromLoading("""
                    {''foo}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and a get-word cannot name either")
        void aColonThenASigil() {
            assertThat(errorIdFromLoading("""
                    {::foo}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {:'foo}""")).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("a sigil cannot name a number")
    class NamingANumber {

        @Test
        @DisplayName("a lit-word cannot start with a digit")
        void aTickThenADigit() {
            assertThat(errorIdFromLoading("""
                    {'2nd}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {'1}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("nor quote a signed one")
        void aTickThenASignedNumber() {
            assertThat(errorIdFromLoading("""
                    {'-1}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {'+1}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("but the sign on its own is a word, and quoting it is fine")
        void aTickThenASignAlone() {
            assertThat(answerTo("""
                    (load {'-}) = to lit-word! "-\"""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a sigil cannot name the none literal")
    class NamingNone {

        @Test
        @DisplayName("an underscore on its own cannot be quoted or got, and the error says which")
        void aSigilThenAnUnderscore() {
            assertThat(errorIdFromLoading("""
                    {'_}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {:_}""")).isEqualTo("invalid");
            assertThat(answerTo("""
                    e: transcode/error/one "'_" e/arg1""")).isEqualTo("\"word-lit\"");
        }

        @Test
        @DisplayName("and only on its own, because a longer name holding one is a word")
        void anUnderscoreInsideAName() {
            assertThat(answerTo("""
                    (load {'_a}) = to lit-word! "_a\"""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a refinement cannot end in a colon")
    class ARefinementWithAColon {

        @Test
        @DisplayName("a trailing colon makes it neither a refinement nor a set-word")
        void theTrailingColon() {
            assertThat(errorIdFromLoading("""
                    {/a:}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and an ordinary refinement is still a refinement")
        void whatSurvives() {
            assertThat(answerTo("""
                    (load {/a}) = /a""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    type? load {/local}""")).isEqualTo("#(refinement!)");
        }

        @Test
        @DisplayName("and more than one slash before a name is refused")
        void tooManySlashes() {
            assertThat(errorIdFromLoading("""
                    {///refine}""")).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("and the one shape the C allows that reads like a mistake")
    class TheAllowedOddity {

        @Test
        @DisplayName("a lit-word of slashes is legal, and MOLD writes one")
        void aQuotedRunOfSlashes() {
            assertThat(answerTo("""
                    (load {'/}) = to lit-word! "/\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {'///}) = to lit-word! "///\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load mold to lit-word! "///") = to lit-word! "///\""""))
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but only when nothing follows the run")
        void unlessSomethingFollows() {
            assertThat(errorIdFromLoading("""
                    {'//x}""")).isEqualTo("invalid");
        }
    }
}
