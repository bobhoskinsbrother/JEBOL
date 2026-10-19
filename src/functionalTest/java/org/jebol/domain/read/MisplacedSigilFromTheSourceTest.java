package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MisplacedSigilFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFromLoading(String source) {
        return answerTo("e: try [load " + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("a sigil cannot follow a sigil")
    class TwoSigils {

        @Test
        @DisplayName("a lit-word cannot name a get-word")
        void refuseATickThenAColon() {
            assertThat(readTheErrorIdFromLoading("""
                    {':a}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {':a:}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("nor another lit-word")
        void refuseATickThenATick() {
            assertThat(readTheErrorIdFromLoading("""
                    {''foo}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and a get-word cannot name either")
        void refuseAColonThenASigil() {
            assertThat(readTheErrorIdFromLoading("""
                    {::foo}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {:'foo}""")).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("a sigil cannot name a number")
    class NamingANumber {

        @Test
        @DisplayName("a lit-word cannot start with a digit")
        void refusesATickThenADigit() {
            assertThat(readTheErrorIdFromLoading("""
                    {'2nd}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {'1}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("nor quote a signed one")
        void refusesATickThenASignedNumber() {
            assertThat(readTheErrorIdFromLoading("""
                    {'-1}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {'+1}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("but the sign on its own is a word, and quoting it is fine")
        void allowsATickThenASignAlone() {
            assertThat(answerTo("""
                    (load {'-}) = to lit-word! "-\"""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a sigil cannot name the none literal")
    class NamingNone {

        @Test
        @DisplayName("an underscore on its own cannot be quoted or got, and the error says which")
        void refusesASigilThenAnUnderscore() {
            assertThat(readTheErrorIdFromLoading("""
                    {'_}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {:_}""")).isEqualTo("invalid");
            assertThat(answerTo("""
                    e: transcode/error/one "'_" e/arg1""")).isEqualTo("\"word-lit\"");
        }

        @Test
        @DisplayName("and only on its own, because a longer name holding one is a word")
        void allowsAnUnderscoreInsideAName() {
            assertThat(answerTo("""
                    (load {'_a}) = to lit-word! "_a\"""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a refinement cannot end in a colon")
    class ARefinementWithAColon {

        @Test
        @DisplayName("a trailing colon makes it neither a refinement nor a set-word")
        void isNeitherARefinementNorASetWord() {
            assertThat(readTheErrorIdFromLoading("""
                    {/a:}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and an ordinary refinement is still a refinement")
        void leavesAnOrdinaryRefinementAlone() {
            assertThat(answerTo("""
                    (load {/a}) = /a""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    type? load {/local}""")).isEqualTo("#(refinement!)");
        }

        @Test
        @DisplayName("and more than one slash before a name is refused")
        void tooManySlashes() {
            assertThat(readTheErrorIdFromLoading("""
                    {///refine}""")).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("and the one shape the C allows that reads like a mistake")
    class TheAllowedOddity {

        @Test
        @DisplayName("a lit-word of slashes is legal, and MOLD writes one")
        void allowsAQuotedRunOfSlashes() {
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
            assertThat(readTheErrorIdFromLoading("""
                    {'//x}""")).isEqualTo("invalid");
        }
    }
}
