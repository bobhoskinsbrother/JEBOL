package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignBeforeHashFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("the sign parts from every hash form")
    class EveryHashForm {

        @Test
        @DisplayName("from a character, either sign")
        void aCharacter() {
            assertThat(answerTo("""
                    [- #"a"] = load {-#"a"}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    [+ #"a"] = load {+#"a"}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("from a binary")
        void aBinary() {
            assertThat(answerTo("""
                    [- #{00}] = load {-#{00}}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("from a construction form")
        void aConstructionForm() {
            assertThat(answerTo("""
                    [- #(none)] = load {-#(none)}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and from an issue")
        void anIssue() {
            assertThat(answerTo("""
                    [- #hhh] = load {-#hhh}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("what the rule is actually for")
    class TheRangeWithoutSpaces {

        @Test
        @DisplayName("a character range written without spaces means what the spaced one means")
        void aCharsetRange() {
            assertThat(answerTo("""
                    (charset [#"a"-#"z"]) = (charset [#"a" - #"z"])"""))
                    .isEqualTo(TRUE);
            assertThat(answerTo("""
                    bitset? charset [#"a"-#"z"]""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and the range really holds the characters between its ends")
        void theRangeWorks() {
            assertThat(answerTo("""
                    find charset [#"a"-#"z"] #"m\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    find/case charset [#"a"-#"z"] #"A\"""")).isEqualTo("#(false)");
            assertThat(answerTo("""
                    find/case charset [#"a"-#"z"] #"0\"""")).isEqualTo("#(false)");
        }
    }

    @Nested
    @DisplayName("and a digit after the sign still binds to it")
    class ANumberIsUnaffected {

        @Test
        @DisplayName("a signed number is one value")
        void aSignedNumber() {
            assertThat(answerTo("""
                    -1""")).isEqualTo("-1");
            assertThat(answerTo("""
                    integer? load {-1}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    -1.5""")).isEqualTo("-1.5");
        }

        @Test
        @DisplayName("and a sign with a space after it is the word, as it always was")
        void aSignAlone() {
            assertThat(answerTo("""
                    [- #"a"] = load {- #"a"}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (first load {- 1}) = to word! "-\"""")).isEqualTo(TRUE);
        }
    }
}
