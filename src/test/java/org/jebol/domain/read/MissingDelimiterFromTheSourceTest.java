package org.jebol.domain.read;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * An unbalanced delimiter is one id with two arguments: the first names the kind
 * of token the reader ran out of -- the C's three spellings end-of-script,
 * end-of-block and end-of-paren -- and the second is the delimiter that would
 * have settled it. Rebol's own tests read the second argument directly.
 */
class MissingDelimiterFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a delimiter that was never closed")
    class TheUnclosedDelimiter {

        @Test
        @DisplayName("an open block runs out of script and wants a bracket")
        void anOpenBlock() {
            assertThat(answerTo("""
                    e: try [load "[1"]
                    all [e/id = 'missing e/arg1 = "end-of-script" e/arg2 = "]"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an open paren wants a parenthesis")
        void anOpenParen() {
            assertThat(answerTo("""
                    e: try [load "(1"]
                    all [e/id = 'missing e/arg1 = "end-of-script" e/arg2 = ")"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the inner of two open blocks is the one reported")
        void theInnerOfTwoOpenBlocks() {
            assertThat(answerTo("""
                    e: try [load "[[1]"]
                    all [e/id = 'missing e/arg2 = "]"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a balanced source loads and raises nothing")
        void theBalancedSourceIsTheOffPoint() {
            assertThat(answerTo("""
                    block? load {[1]}""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a delimiter that closes something never opened")
    class TheStrayCloser {

        @Test
        @DisplayName("a stray bracket ends a block that was not open")
        void aStrayBracket() {
            assertThat(answerTo("""
                    e: try [load "1]"]
                    all [e/id = 'missing e/arg1 = "end-of-block" e/arg2 = "["]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a stray parenthesis ends a paren that was not open")
        void aStrayParenthesis() {
            assertThat(answerTo("""
                    e: try [load "1)"]
                    all [e/id = 'missing e/arg1 = "end-of-paren" e/arg2 = "("]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a bracket closing an open paren names the paren it interrupted")
        void aBracketClosingAParen() {
            assertThat(answerTo("""
                    e: try [load "[(]"]
                    all [e/id = 'missing e/arg1 = "end-of-block" e/arg2 = ")"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a lone closer on its own is refused as well")
        void aLoneCloser() {
            assertThat(answerTo("""
                    error? try [load "]"]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what is not a delimiter has its own id")
    class TheOtherFailures {

        @Test
        @DisplayName("an unterminated string is not a missing delimiter")
        void anUnterminatedString() {
            assertThat(answerTo("""
                    e: try [load {"abc}] e/id = 'unterminated-string"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an unterminated binary is an invalid token naming its kind")
        void anUnterminatedBinary() {
            assertThat(answerTo("""
                    e: try [load "#{01"]
                    all [e/id = 'invalid e/arg1 = "binary"]""")).isEqualTo("#(true)");
        }
    }
}
