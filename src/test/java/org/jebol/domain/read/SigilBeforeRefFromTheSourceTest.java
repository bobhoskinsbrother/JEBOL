package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A sigil cannot go in front of an at-sign, from the first line of
 * {@code LEX_CLASS_SPECIAL} in {@code Scan_Token}.
 *
 * <pre>
 * if (HAS_LEX_FLAG(flags, LEX_SPECIAL_AT) &amp;&amp; *cp != '&lt;' &amp;&amp; *cp != '%') {
 *     if (*cp == '\'' || *cp == ':') return -TOKEN_WORD; // no '@foo abd :@foo
 * </pre>
 *
 * <p>It is the first thing the special class checks, before any of the per-character
 * cases. An at-sign makes a ref or an email -- datatypes of their own -- and a sigil
 * names a <em>word</em>, so there is nothing for it to name.
 *
 * <p>The test is on the flag rather than on the character after the sigil, so the
 * at-sign anywhere in the lexeme is enough: {@code 'a@b} is refused as readily as
 * {@code '@foo}.
 *
 * <p>And the two exceptions in that condition are the reason it has to be a flag
 * test rather than a plain scan. A tag may hold an at-sign -- {@code <a@b>} -- and so
 * may a file whose percent escape happens to decode to one, which the C's own comment
 * names: "for case like: %61@b which is actually: a@b".
 */
class SigilBeforeRefFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFromLoading(String source) {
        return answerTo("e: try [load " + source + "] "
                + "either error? e [e/id] ['no-error]");
    }

    private static final String TRUE = "#(true)";

    @Nested
    @DisplayName("the sigil is refused")
    class TheRefusal {

        @Test
        @DisplayName("in front of a ref")
        void beforeARef() {
            assertThat(errorIdFromLoading("""
                    {'@foo}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {:@foo}""")).isEqualTo("invalid");
        }

        @Test
        @DisplayName("and in front of an email, because the test is on the at-sign anywhere")
        void beforeAnEmail() {
            assertThat(errorIdFromLoading("""
                    {'a@b}""")).isEqualTo("invalid");
            assertThat(errorIdFromLoading("""
                    {:a@b}""")).isEqualTo("invalid");
        }
    }

    @Nested
    @DisplayName("and what the at-sign makes without a sigil")
    class WithoutASigil {

        @Test
        @DisplayName("a leading at-sign is a ref")
        void aRef() {
            assertThat(answerTo("""
                    ref? load {@foo}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an at-sign inside a name is an email")
        void anEmail() {
            assertThat(answerTo("""
                    email? load {a@b}""")).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("and the two exceptions the C carves out")
    class TheExceptions {

        @Test
        @DisplayName("a tag may hold an at-sign")
        void aTagMayHoldOne() {
            assertThat(answerTo("""
                    tag? load {<a@b>}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and so may a file, including one whose escape decodes to it")
        void aFileMayHoldOne() {
            assertThat(answerTo("""
                    file? load {%a@b}""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    file? load {%61@b}""")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and an ordinary sigil'd word is untouched")
        void ordinarySigils() {
            assertThat(answerTo("""
                    (load {'foo}) = to lit-word! "foo\"""")).isEqualTo(TRUE);
            assertThat(answerTo("""
                    (load {:foo}) = to get-word! "foo\"""")).isEqualTo(TRUE);
        }
    }
}
