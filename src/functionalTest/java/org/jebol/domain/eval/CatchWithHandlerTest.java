package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatchWithHandlerTest {

    private static final String HANDLER =
            "on-catch: func [value [any-type!] name] ["
                    + "  if :name = 'foo [return join \"b\" :value] "
                    + "  if unset? :value [return true] "
                    + "  if integer? :value [return value * 10] "
                    + "  mold value"
                    + "] ";

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a RETURN inside the handler answers rather than escaping")
    void theHandlerMayReturnEarly() {
        assertThat(answerTo(HANDLER + "catch/with [a: 1 throw 3 a: 2] :on-catch"))
                .isEqualTo("30");
    }

    @Test
    @DisplayName("the block after the throw is not run")
    void theThrowStopsTheBlock() {
        assertThat(answerTo(HANDLER + "a: 0 catch/with [a: 1 throw 3 a: 2] :on-catch a"))
                .isEqualTo("1");
    }

    @Test
    @DisplayName("the handler is given the name the throw carried")
    void theHandlerSeesTheName() {
        assertThat(answerTo(
                HANDLER + "\"b3\" = catch/all/with [a: 1 throw/name 3 'foo a: 2] :on-catch"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("an unnamed throw gives the handler none, not nothing")
    void anUnnamedThrowStillNamesSomething() {
        assertThat(answerTo(
                "seen: none h: func [value [any-type!] name] [seen: :name 1] "
                        + "catch/with [throw 3] :h none? seen"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the handler does not run when nothing was thrown")
    void theHandlerIsNotAFinally() {
        assertThat(answerTo(HANDLER + "catch/with [1 + 1] :on-catch")).isEqualTo("2");
    }

    @Test
    @DisplayName("a block handler still works")
    void aBlockHandlerIsUnaffected() {
        assertThat(answerTo("catch/with [throw 3] [99]")).isEqualTo("99");
    }

    @Test
    @DisplayName("a plain CATCH is unaffected")
    void theOrdinaryCatchStillWorks() {
        assertThat(answerTo("catch [throw 3]")).isEqualTo("3");
    }

    @Test
    @DisplayName("a function applied with too few arguments gets unset, not a missing word")
    void theMissingArgumentsAreUnset() {
        assertThat(answerTo(
                "h: func [value [any-type!] name] [unset? :name] catch/with [throw 3] :h"))
                .isEqualTo("#(false)");
    }
}
