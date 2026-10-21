package org.jebol.domain.read;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileLiteralFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String readTheErrorIdFromLoading(String source) {
        return answerTo("e: try [load " + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("what an unquoted name refuses, which is two characters and not eight")
    class TheRefusedSet {

        @Test
        @DisplayName("a colon is not part of a file name")
        void includeTheColon() {
            assertThat(readTheErrorIdFromLoading("""
                    {%a:b}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and a delimiter ends the name instead of invalidating it")
        void endAtADelimiter() {
            assertThat(answerTo("""
                    mold load {%a b}""")).isEqualTo("\"[%a b]\"");
            assertThat(answerTo("""
                    (first load {(%a)}) = %a""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (second load {[%a] %b}) = %b""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    mold load {%a;b}""")).isEqualTo("\"%a\"");
        }

        @Test
        @DisplayName("and everything else is an ordinary character of a name")
        void leaveEverythingElseOrdinary() {
            assertThat(answerTo("""
                    mold load {%a-b.txt}""")).isEqualTo("\"%a-b.txt\"");
            assertThat(answerTo("""
                    mold load {%a&b#c}""")).isEqualTo("\"%a&b#c\"");
            assertThat(answerTo("""
                    mold load {%a/b/c}""")).isEqualTo("\"%a/b/c\"");
        }
    }

    @Nested
    @DisplayName("the caret, which is an escape everywhere but here")
    class TheCaret {

        @Test
        @DisplayName("a caret in an unquoted name is refused rather than read as an escape")
        void isRefusedInAnUnquotedName() {
            assertThat(readTheErrorIdFromLoading("""
                    {%^^}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {%a^^b}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {%a^^ }""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and inside a quoted name it escapes, even the closing quote")
        void escapesInAQuotedName() {
            assertThat(readTheErrorIdFromLoading("""
                    {%"a^^"}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and a caret in a quoted name reads the character after it twice, as r3's Scan_Item does")
        void readsTheNextCharacterTwiceInAQuotedName() {
            assertThat(answerTo("""
                    (load {%"a^^-b"}) = to file! {a--b}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    length? load {%"a^^-b"}""")).isEqualTo("4");
        }
    }

    @Nested
    @DisplayName("the percent escape, which wants two hex digits")
    class ThePercentEscape {

        @Test
        @DisplayName("two hex digits are read as the byte they name")
        void readsTwoHexDigitsAsTheirByte() {
            assertThat(answerTo("""
                    (load {%a%20b}) = to file! "a b\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {%a%2Bb}) = to file! "a+b\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and anything else after the percent is refused")
        void refusesAnythingElse() {
            assertThat(readTheErrorIdFromLoading("""
                    {%a%2h}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {%a%zz}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {%a%2}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("an email is checked the same way, by its own scanner")
        void checksAnEmailTheSameWay() {
            assertThat(readTheErrorIdFromLoading("""
                    {a@%2h}""")).isEqualTo("invalid");
            assertThat(answerTo("""
                    (load {a@%20b}) = to email! "a@ b\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an email wants exactly one at-sign")
        void wantsExactlyOneAtSignInAnEmail() {
            assertThat(readTheErrorIdFromLoading("""
                    {a@b@c}""")).isEqualTo("invalid");
            assertThat(answerTo("""
                    email? load {a@b}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a quoted name, where the rules relax")
    class AQuotedName {

        @Test
        @DisplayName("a quoted name may hold what an unquoted one may not")
        void holdsWhatAnUnquotedOneMayNot() {
            assertThat(answerTo("""
                    (load {%"a b"}) = to file! "a b\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {%"a(b"}) = to file! "a(b\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {%"a]b"}) = to file! "a]b\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a colon and a semicolon are still refused")
        void stillRefusesAColonAndASemicolon() {
            assertThat(readTheErrorIdFromLoading("""
                    {%"a:b"}""")).isEqualTo("invalid");
            assertThat(readTheErrorIdFromLoading("""
                    {%"a;b"}""")).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("two rules that are easy to miss")
    class TheQuietOnes {

        @Test
        @DisplayName("a backslash quietly becomes a forward slash")
        void turnABackslashIntoASlash() {
            assertThat(answerTo("""
                    mold load {%a\\b}""")).isEqualTo("\"%a/b\"");
        }

        @Test
        @DisplayName("and a tab or a control character ends the name, as a space does")
        void endTheNameAtAControlCharacter() {
            assertThat(answerTo("""
                    mold load {%a^-b}""")).isEqualTo("\"[%a b]\"");
            assertThat(answerTo("""
                    mold load {%a^(01)b}""")).isEqualTo("\"[%a b]\"");
        }
    }
}
