package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
