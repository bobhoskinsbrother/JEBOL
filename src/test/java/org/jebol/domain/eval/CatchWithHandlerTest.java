package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CATCH/WITH, whose handler is a function that may RETURN.
 *
 * <p>Specified in {@code spec/natives.allium} and measured against a real
 * R3 3.22.1.
 *
 * <p>A function reached by CATCH rather than by name returns the same way
 * as any other. Without that, a RETURN inside the handler escaped the
 * interpreter as a Java exception, which {@code spec/embed.allium} says
 * cannot happen: a host must be able to tell a script's failure from a bug
 * in the interpreter.
 *
 * <p>The handler is given two things, the value and the name the throw
 * carried, and an unnamed throw gives none rather than nothing so the
 * handler can ask.
 */
class CatchWithHandlerTest {

    /** A handler that returns early, which is what used to escape. */
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
        // The handler asks about the name, so it has to be a value. Left
        // undefined, the body fails on a missing word instead.
        assertThat(answerTo(
                "seen: none h: func [value [any-type!] name] [seen: :name 1] "
                        + "catch/with [throw 3] :h none? seen"))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("the handler does not run when nothing was thrown")
    void theHandlerIsNotAFinally() {
        // /WITH runs on a throw and not on the way out, so this answers
        // the block's own value.
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
        // The same fix seen from the other side: every parameter is
        // defined whether or not a value came for it.
        assertThat(answerTo(
                "h: func [value [any-type!] name] [unset? :name] catch/with [throw 3] :h"))
                .isEqualTo("#(false)");
    }
}
