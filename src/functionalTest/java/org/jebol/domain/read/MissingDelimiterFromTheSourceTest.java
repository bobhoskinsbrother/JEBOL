package org.jebol.domain.read;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MissingDelimiterFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Nested
    @DisplayName("a delimiter that was never closed")
    class TheUnclosedDelimiter {

        @Test
        @DisplayName("an open block runs out of script and wants a bracket")
        void wantsABracketForAnOpenBlock() {
            assertThat(answerTo("""
                    e: try [load "[1"]
                    all [e/id = 'missing e/arg1 = "end-of-script" e/arg2 = "]"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an open paren wants a parenthesis")
        void wantsAParenthesisForAnOpenParen() {
            assertThat(answerTo("""
                    e: try [load "(1"]
                    all [e/id = 'missing e/arg1 = "end-of-script" e/arg2 = ")"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and the inner of two open blocks is the one reported")
        void isTheInnerOfTwoOpenBlocks() {
            assertThat(answerTo("""
                    e: try [load "[[1]"]
                    all [e/id = 'missing e/arg2 = "]"]""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a balanced source loads and raises nothing")
        void raisesNothingForABalancedSource() {
            assertThat(answerTo("""
                    block? load {[1]}""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("a delimiter that closes something never opened")
    class TheStrayCloser {

        @Test
        @DisplayName("a stray bracket ends a block that was not open")
        void endsABlockThatWasNotOpen() {
            assertThat(answerTo("""
                    e: try [load "1]"]
                    all [e/id = 'missing e/arg1 = "end-of-block" e/arg2 = "["]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a stray parenthesis ends a paren that was not open")
        void endsAParenThatWasNotOpen() {
            assertThat(answerTo("""
                    e: try [load "1)"]
                    all [e/id = 'missing e/arg1 = "end-of-paren" e/arg2 = "("]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("a bracket closing an open paren names the paren it interrupted")
        void namesTheParenABracketInterrupted() {
            assertThat(answerTo("""
                    e: try [load "[(]"]
                    all [e/id = 'missing e/arg1 = "end-of-block" e/arg2 = ")"]"""))
                    .isEqualTo("#(true)");
        }

        @Test
        @DisplayName("and a lone closer on its own is refused as well")
        void isRefusedOnItsOwn() {
            assertThat(answerTo("""
                    error? try [load "]"]""")).isEqualTo("#(true)");
        }
    }

    @Nested
    @DisplayName("what is not a delimiter has its own id")
    class TheOtherFailures {

        @Test
        @DisplayName("an unterminated string is not a missing delimiter")
        void reportAnUnterminatedStringByItsOwnId() {
            assertThat(answerTo("""
                    e: try [load {"abc}] e/id = 'invalid""")).isEqualTo("#(true)");
        }

        @Test
        @DisplayName("an unterminated binary is an invalid token naming its type")
        void reportAnUnterminatedBinaryAsAnInvalidToken() {
            assertThat(answerTo("""
                    e: try [load "#{01"]
                    all [e/id = 'invalid e/arg1 = "binary"]""")).isEqualTo("#(true)");
        }
    }
}
