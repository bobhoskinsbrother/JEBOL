package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code system/state}, which records what just happened.
 *
 * <p>Specified in {@code spec/natives.allium}, confirmed against a real R3.
 *
 * <p>It is what lets a handler that was given no arguments find out what
 * it is handling. A /WITH handler written as a function can read its
 * argument; one written as a block has only this.
 */
class SystemStateTest {

    private static String answerTo(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("last-error holds what the most recent TRY caught")
    void lastErrorRemembersTheFailure() {
        assertThat(answerTo("try [1 / 0] system/state/last-error/id"))
                .isEqualTo("zero-divide");
    }

    @Test
    @DisplayName("last-error is none before anything has failed")
    void lastErrorStartsEmpty() {
        assertThat(answerTo("mold system/state/last-error")).isEqualTo("\"_\"");
    }

    @Test
    @DisplayName("last-error is replaced by the next failure")
    void lastErrorKeepsOnlyTheMostRecent() {
        assertThat(answerTo(
                "try [1 / 0] try [poke b: [1] 9 0] system/state/last-error/id"))
                .isEqualTo("out-of-range");
    }

    @Test
    @DisplayName("last-result holds what the most recent CATCH carried")
    void lastResultRemembersTheThrow() {
        assertThat(answerTo("catch [throw 3] system/state/last-result")).isEqualTo("3");
    }

    @Test
    @DisplayName("a block handler reaches the thrown value through last-result")
    void aBlockHandlerCanReadIt() {
        // The whole reason the pair exists: a block has no argument.
        assertThat(answerTo("catch/with [throw 3] [system/state/last-result * 10]"))
                .isEqualTo("30");
    }

    @Test
    @DisplayName("a block handler reaches a QUIT's value the same way")
    void aQuitHandlerCanReadItToo() {
        assertThat(answerTo(
                "catch/quit/with [quit/return 4] [system/state/last-result * 10]"))
                .isEqualTo("40");
    }

    @Test
    @DisplayName("system/options names the places the interpreter was started from")
    void theOptionsAreFiles() {
        assertThat(answerTo("mold reduce [file? system/options/home "
                + "file? system/options/boot]"))
                .isEqualTo("\"[#(true) #(true)]\"");
    }
}
