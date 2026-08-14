package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A sign directly against a hash form is the sign alone, from the plus and minus
 * case of {@code Scan_Token}.
 *
 * <pre>
 * cp++;
 * if (IS_LEX_AT_LEAST_NUMBER(*cp)) goto num;
 * if (IS_LEX_SPECIAL(*cp)) {
 *     if (*cp == '#') {
 *         scan_state-&gt;end = cp;
 *         return TOKEN_WORD;
 *     }
 * </pre>
 *
 * <p>{@code scan_state->end = cp} sets the token's end <em>at</em> the hash, so the
 * token is the one character before it. The sign is a word and the hash form is read
 * afresh as a value of its own.
 *
 * <p>Which matters for what it unblocks rather than for its own sake. Rebol logs
 * this as issue #2319, and the case that made somebody file it is
 * {@code charset [#"a"-#"z"]} -- a character range written without spaces, which has
 * to mean the same as {@code charset [#"a" - #"z"]}. Read the other way it was the
 * word {@code -#} followed by a string, which is not a range and not anything.
 *
 * <p>A digit after the sign still binds to it, which is the line above:
 * {@code -1} is one value and always was.
 */
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
                    find charset [#"a"-#"z"] #"A\"""")).isEqualTo("#(false)");
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
