package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QUIT ends the run and nothing else.
 *
 * <p>Specified by {@code QuitEndsTheRunWithoutEndingTheHost} in
 * {@code spec/embed.allium}. In a real REBOL, QUIT ends the process as well,
 * because there the script is the process. Here a script is a guest: the
 * host decides whether anything exits, and a guest that could take the host
 * down with it is not embeddable.
 *
 * <p>The boundaries are the value QUIT carries (absent, and then each kind
 * of value that is easy to confuse with absent), and how deeply nested the
 * call is when it happens.
 */
class QuitEndsTheRunTest {

    private static ScriptOutcome quitting(String source) {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn(source);
        return interpreter.run(source);
    }

    @Test
    @DisplayName("QUIT on its own ends the run and carries nothing")
    void aBareQuitCarriesNothing() {
        ScriptOutcome outcome = quitting("quit");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(outcome.value().datatype().literalSpelling())
                .as("a bare QUIT carries nothing, which is unset and not none")
                .isEqualTo("unset!");
    }

    @Test
    @DisplayName("QUIT/RETURN carries the value it was given")
    void quitReturnCarriesItsValue() {
        ScriptOutcome outcome = quitting("quit/return 42");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(Interpreter.create().display(outcome)).isEqualTo("42");
    }

    @Test
    @DisplayName("QUIT/RETURN carries values that look like nothing")
    void quitReturnCarriesDegenerateValues() {
        assertThat(Interpreter.create().display(quitting("quit/return 0"))).isEqualTo("0");
        assertThat(Interpreter.create().display(quitting("quit/return \"\"")))
                .isEqualTo("\"\"");
        assertThat(Interpreter.create().display(quitting("quit/return []")))
                .isEqualTo("[]");
        assertThat(Interpreter.create().display(quitting("quit/return false")))
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("QUIT/RETURN none is a quit that carries none on purpose")
    void quitReturnNoneIsStillAQuit() {
        ScriptOutcome outcome = quitting("quit/return none");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(outcome.value().datatype().literalSpelling()).isEqualTo("none!");
    }

    @Test
    @DisplayName("QUIT inside a function ends the whole run, not the function")
    void quitFromInsideAFunctionEndsEverything() {
        ScriptOutcome outcome = quitting(
                "f: func [] [quit/return 1] f 99");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(Interpreter.create().display(outcome))
                .as("the 99 after the call must never be reached")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("QUIT inside a loop ends the whole run")
    void quitFromInsideALoopEndsEverything() {
        ScriptOutcome outcome = quitting("repeat n 10 [if n = 3 [quit/return n]] 99");

        assertThat(Interpreter.create().display(outcome)).isEqualTo("3");
    }

    @Test
    @DisplayName("TRY does not catch a QUIT, because a QUIT is not a failure")
    void tryDoesNotCatchAQuit() {
        ScriptOutcome outcome = quitting("try [quit/return 5] 99");

        assertThat(outcome.conclusion())
                .as("TRY catches errors; stopping on purpose is not one")
                .isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(Interpreter.create().display(outcome)).isEqualTo("5");
    }

    @Test
    @DisplayName("CATCH does not catch a QUIT either")
    void catchDoesNotCatchAQuit() {
        ScriptOutcome outcome = quitting("catch [quit/return 5] 99");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(Interpreter.create().display(outcome)).isEqualTo("5");
    }

    @Test
    @DisplayName("QUIT/NOW is accepted and behaves the same")
    void quitNowIsAccepted() {
        ScriptOutcome outcome = quitting("quit/now");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
    }

    @Test
    @DisplayName("the interpreter still works after a script quits")
    void theHostSurvivesAQuit() {
        Interpreter interpreter = Interpreter.create();
        interpreter.defineFreshWordsIn("kept: 7 quit/return 1");
        interpreter.run("kept: 7 quit/return 1");

        assertThat(interpreter.display(interpreter.run("kept + 1")))
                .as("a guest stopping must not take the host with it")
                .isEqualTo("8");
    }

    @Test
    @DisplayName("QUIT can be taken as a value, which is how Rebol's own library uses it")
    void quitCanBeAliased() {
        Interpreter interpreter = Interpreter.create();

        assertThat(interpreter.display(interpreter.run("q: :quit type? :q")))
                .as("base-constants.reb writes `q: :quit` and stopped there without this")
                .isEqualTo("#(native!)");
    }
}
