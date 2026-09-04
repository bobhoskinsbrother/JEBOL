package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CATCH corners from {@code evaluation-test.r3}: a bare CATCH/QUIT catches only
 * quit and halt, never a throw; a CATCH/WITH function handler is type-checked
 * and its surplus parameters default to none; and a set-path whose last segment
 * is a paren evaluates it, firing any throw inside.
 */
class CatchHandlerAndPathFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("a bare CATCH/QUIT lets a throw pass to an outer CATCH")
    void bareCatchQuitDoesNotCatchAThrow() {
        assertThat(answerTo("""
                catch [catch/quit [throw 5]]""")).isEqualTo("5");
    }

    @Test
    @DisplayName("a bare CATCH/QUIT does not run what follows the swallowed throw")
    void bareCatchQuitDoesNotRunPastTheThrow() {
        assertThat(answerTo("""
                a: 1 catch [catch/quit [throw 'x a: 99] a: 0] a""")).isEqualTo("1");
    }

    @Test
    @DisplayName("CATCH/QUIT still catches a quit")
    void catchQuitStillCatchesAQuit() {
        assertThat(answerTo("""
                catch/quit [quit/return 7]""")).isEqualTo("7");
    }

    @Test
    @DisplayName("a handler whose value parameter refuses the caught value raises expect-arg")
    void handlerValueTypeIsChecked() {
        assertThat(answerTo("""
                e: try [catch/with [throw 1] func [v [word!]] []] e/id = 'expect-arg"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a handler whose name parameter refuses the throw's name raises expect-arg")
    void handlerNameTypeIsChecked() {
        assertThat(answerTo("""
                e: try [catch/all/with [throw/name 1 'foo] func [v n [integer!]] []]
                e/id = 'expect-arg""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a handler with surplus parameters sees them as none")
    void handlerSurplusParametersDefaultToNone() {
        assertThat(answerTo("""
                (reduce [1 none none])
                    = catch/with [throw 1] func [a b c] [reduce [a b c]]"""))
                .isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a set-path with a paren last segment evaluates it, firing a throw")
    void setPathParenLastSegmentEvaluates() {
        assertThat(answerTo("""
                foo: make object! [bar: 1]
                catch [foo/(throw "ok" 'bar): 3]""")).isEqualTo("\"ok\"");
    }
}
