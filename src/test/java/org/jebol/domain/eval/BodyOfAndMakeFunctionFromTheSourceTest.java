package org.jebol.domain.eval;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BODY-OF and MAKE on a function, read from {@code func-test.r3}: BODY-OF
 * answers a copy so a change to it does not reach the function (issue-166),
 * and MAKE from a native prototype refuses a body block (issue-1052).
 */
class BodyOfAndMakeFunctionFromTheSourceTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String erroredOn(String source) {
        return answerTo("error? try [" + source + "]");
    }

    @Test
    @DisplayName("BODY-OF answers the body")
    void bodyOfAnswersTheBody() {
        assertThat(answerTo("""
                f: func [a [integer!]] [probe a]
                [probe a] = body-of :f""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("clearing part of BODY-OF does not reach into the function")
    void clearingBodyOfDoesNotReachTheFunction() {
        assertThat(answerTo("""
                f: func [a] [append {xx} s]
                clear second body-of :f
                [append {xx} s] = body-of :f""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("MAKE from a native prototype with a body block is refused")
    void makeFromANativeWithABodyIsRefused() {
        assertThat(erroredOn("make :read [[][]]")).isEqualTo("#(true)");
        assertThat(erroredOn("make action! [[][]]")).isEqualTo("#(true)");
        assertThat(erroredOn("make native! [[][]]")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("deriving a wider interface from a native with one spec block still works")
    void derivingWithOneSpecBlockStillWorks() {
        assertThat(answerTo("""
                wider: make :tail? [[series [series! none!]]]
                any-function? :wider""")).isEqualTo("#(true)");
    }
}
