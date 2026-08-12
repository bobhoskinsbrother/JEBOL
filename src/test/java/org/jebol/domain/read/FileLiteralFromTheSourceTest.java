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
            // The colon is not a delimiter, so it stays inside the token and
            // `Scan_File`'s refused set turns it away.
            assertThat(errorIdFromLoading("""
                    {%a:b}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and a delimiter ends the name instead of invalidating it")
        void theDelimitersEndIt() {
            // The ordering, stated as behaviour. `%a(b` is a file and an open
            // paren, not a refused file. Reading the refused set without the
            // delimiting stage makes all of these `invalid` and takes every
            // ordinary `(clean-path %a/b)` with them.
            assertThat(answerTo("""
                    mold load {%a b}""")).isEqualTo("\"[%a b]\"");
            assertThat(answerTo("""
                    (first load {(%a)}) = %a""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (second load {[%a] %b}) = %b""")).isEqualTo(TRUE);
            // A semicolon starts a comment, so it ends the name and eats the rest.
            assertThat(answerTo("""
                    mold load {%a;b}""")).isEqualTo("\"%a\"");
        }

        @Test
        @DisplayName("and everything else is an ordinary character of a name")
        void whatSurvives() {
            // The refused set is short and the delimiters are few, so most
            // punctuation is ordinary: a dot, a dash, an ampersand, a hash.
            assertThat(answerTo("""
                    mold load {%a-b.txt}""")).isEqualTo("\"%a-b.txt\"");
            assertThat(answerTo("""
                    mold load {%a&b#c}""")).isEqualTo("\"%a&b#c\"");
            // And a slash, which the file case walks over on purpose so that a
            // path is one file: `while (*cp == '/') { cp++; ... }`.
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
            // `if (src + 1 == end || (invalid && strchr(cs_cast(invalid), chr)))
            // return 0; // nothing follows ^ or used in unquoted file` -- and the
            // caret is in the unquoted name's refused set, so the second half of
            // that test is what fires. Three of Rebol's own assertions, and the
            // braces are what carry the caret to the reader unescaped.
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
            // Off the refused set, so `Scan_Char` reads it -- which means a caret
            // before the closing quote escapes *that*, and the literal loses its
            // terminator. So the failure is unterminated-string and not invalid:
            // the read got further than it looks.
            assertThat(errorIdFromLoading("""
                    {%"a^^"}""")).isEqualTo("unterminated-string");
        }

        @Test
        @DisplayName("and a tab in a name is spellable only through the quoted form")
        void aQuotedNameHoldsATab() {
            // Which is what the quoted form is for. Asserted as the character the
            // escape produced rather than against another literal, because
            // comparing two escaped spellings only moves the problem.
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
            // `if (!Scan_Hex2(src, &chr)) return 0; src += 2;` -- so %20 in a
            // name is a space, which is the only way an unquoted name holds one.
            assertThat(answerTo("""
                    (load {%a%20b}) = to file! "a b\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {%a%2Bb}) = to file! "a+b\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and anything else after the percent is refused")
        void notTwoHexDigits() {
            // `2h` is not two hex digits, which is Rebol's own case.
            assertThat(errorIdFromLoading("""
                    {%a%2h}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {%a%zz}""")).isEqualTo("invalid");
            // And a percent with fewer than two characters left after it.
            assertThat(errorIdFromLoading("""
                    {%a%2}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("an email is checked the same way, by its own scanner")
        void anEmailToo() {
            // `Scan_Email` writes the rule out again rather than sharing
            // `Scan_Item`: `if (len <= 2 || !Scan_Hex2(cp+1, &n)) return 0;`.
            // Rebol's own case is `a@%2h`.
            assertThat(errorIdFromLoading("""
                    {a@%2h}""")).isEqualTo("invalid");
            assertThat(answerTo("""
                    (load {a@%20b}) = to email! "a@ b\"""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an email wants exactly one at-sign")
        void oneAtSign() {
            // `if (*cp == '@') { if (at) return 0; at = TRUE; }` on the way
            // through, and `if (!at) return 0;` at the end. Two is as wrong as
            // none.
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
            // `if (*cp == '"') { ... invalid = cb_cast(":;\""); }`, and the quote
            // is the terminator so the delimiters stop applying too. Which is the
            // point of the form: a name holding a space or a bracket has to be
            // spellable.
            //
            // Asserted as what the name holds rather than as how it molds. Molding
            // one that needs its quotes back is a separate gap -- `%a b` molds
            // without them and so does not read back -- and it belongs with the
            // rest of 5c's molding cluster.
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
            // `if (chr == '\\') chr = '/';` -- one line and no comment. So a
            // Windows-shaped path read as a REBOL file comes out with the
            // separator the language uses, and a script never sees a backslash in
            // a file it loaded.
            assertThat(answerTo("""
                    mold load {%a\\b}""")).isEqualTo("\"%a/b\"");
        }

        @Test
        @DisplayName("and a control character is refused, but whitespace ends the name first")
        void aControlCharacterIsRefused() {
            // Three lines, and their order is the whole answer:
            //
            //     if (chr == 0) break;
            //     if (!term && IS_WHITE(chr)) break;
            //     if (chr < ' ') return 0;    // invalid char
            //
            // A tab is below space *and* is whitespace, so it reaches the second
            // line and ends the name: `%a<tab>b` is a file and a word, not a
            // failure. Only a control character that is not whitespace gets as far
            // as the third line.
            assertThat(answerTo("""
                    mold load {%a^-b}""")).isEqualTo("\"[%a b]\"");
            assertThat(errorIdFromLoading("""
                    {%a^(01)b}""")).isEqualTo("invalid");
        }
    }
}
