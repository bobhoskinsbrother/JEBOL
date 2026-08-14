package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a file literal and an email literal may hold, from {@code Scan_Item},
 * {@code Scan_File} and {@code Scan_Email}.
 *
 * <p>A file is not "everything up to the next space", and it is read in two
 * stages that have to be kept apart.
 *
 * <p><b>First the lexer finds where the token ends.</b> {@code scan_state->end}
 * stops at any {@code IS_LEX_DELIMIT} character: whitespace and
 * <code>( ) [ ] { } " ;</code>. The file case then walks on over slashes on
 * purpose, which is what lets a whole path be one file.
 *
 * <p><b>Then {@code Scan_File} checks what was found</b>, against a set of
 * characters to refuse: {@code ":;()[]\"^"} for an unquoted name. Five of those
 * eight are also delimiters, so they ended the token and can never be inside one
 * -- which leaves the <b>colon</b> and the <b>caret</b> as the two that really
 * bite. Getting the ordering wrong makes {@code (clean-path %a/b) = %a/b} a syntax
 * error, because the closing bracket is read as part of the name.
 *
 * <p>The caret is the surprising one, because it is an escape everywhere else in
 * the language. The C says why in a comment beside the check: "checks also if not
 * used in file like: %a^b which must be invalid!". Inside a quoted name the
 * refused set narrows to {@code ":;\""} and the caret becomes an escape again.
 *
 * <p>A percent sign is an escape and wants two hex digits. That rule is in
 * {@code Scan_Item} for a file and written out again in {@code Scan_Email} for an
 * email, which also insists on exactly one at-sign.
 *
 * <p>JEBOL validated none of it and read every one of these as a file.
 */
class FileLiteralFromTheSourceTest {

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
    @DisplayName("what an unquoted name refuses, which is two characters and not eight")
    class TheRefusedSet {

        @Test
        @DisplayName("a colon is not part of a file name")
        void theColon() {
            assertThat(errorIdFromLoading("""
                    {%a:b}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and a delimiter ends the name instead of invalidating it")
        void theDelimitersEndIt() {
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
        void whatSurvives() {
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
        void aCaretIsRefused() {
            assertThat(errorIdFromLoading("""
                    {%^^}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {%a^^b}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {%a^^ }""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and inside a quoted name it escapes, even the closing quote")
        void theCaretEscapesInAQuotedName() {
            assertThat(errorIdFromLoading("""
                    {%"a^^"}""")).isEqualTo("unterminated-string");
        }

        @Test
        @DisplayName("and a tab in a name is spellable only through the quoted form")
        void aQuotedNameHoldsATab() {
            assertThat(answerTo("""
                    (second load {%"a^^-b"}) = to char! 9""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    length? load {%"a^^-b"}""")).isEqualTo("3");
        }
    }

    @Nested
    @DisplayName("the percent escape, which wants two hex digits")
    class ThePercentEscape {

        @Test
        @DisplayName("two hex digits are read as the byte they name")
        void twoHexDigits() {
            assertThat(answerTo("""
                    (load {%a%20b}) = to file! "a b\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {%a%2Bb}) = to file! "a+b\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and anything else after the percent is refused")
        void notTwoHexDigits() {
            assertThat(errorIdFromLoading("""
                    {%a%2h}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {%a%zz}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {%a%2}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("an email is checked the same way, by its own scanner")
        void anEmailToo() {
            assertThat(errorIdFromLoading("""
                    {a@%2h}""")).isEqualTo("invalid");
            assertThat(answerTo("""
                    (load {a@%20b}) = to email! "a@ b\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an email wants exactly one at-sign")
        void oneAtSign() {
            assertThat(errorIdFromLoading("""
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
        void theNarrowerRefusedSet() {
            assertThat(answerTo("""
                    (load {%"a b"}) = to file! "a b\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {%"a(b"}) = to file! "a(b\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {%"a]b"}) = to file! "a]b\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("but a colon and a semicolon are still refused")
        void whatStaysRefused() {
            assertThat(errorIdFromLoading("""
                    {%"a:b"}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {%"a;b"}""")).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("two rules that are easy to miss")
    class TheQuietOnes {

        @Test
        @DisplayName("a backslash quietly becomes a forward slash")
        void aBackslashIsASlash() {
            assertThat(answerTo("""
                    mold load {%a\\b}""")).isEqualTo("\"%a/b\"");
        }

        @Test
        @DisplayName("and a control character is refused, but whitespace ends the name first")
        void aControlCharacterIsRefused() {
            assertThat(answerTo("""
                    mold load {%a^-b}""")).isEqualTo("\"[%a b]\"");
            assertThat(errorIdFromLoading("""
                    {%a^(01)b}""")).isEqualTo("invalid");
        }
    }
}
