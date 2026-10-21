package org.jebol.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuitEndsTheRunTest {

    private static ScriptOutcome runAQuit(String source) {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn(source);
        return interpreter.run(source);
    }

    @Test
    @DisplayName("QUIT on its own ends the run and carries nothing")
    void carriesNothingForABareQuit() {
        ScriptOutcome outcome = runAQuit("quit");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(outcome.value().datatype().literalSpelling())
                .as("a bare QUIT carries nothing, which is unset and not none")
                .isEqualTo("unset!");
    }

    @Test
    @DisplayName("QUIT/RETURN carries the value it was given")
    void quitReturnCarriesItsValue() {
        ScriptOutcome outcome = runAQuit("quit/return 42");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(AnInterpreter.create().display(outcome)).isEqualTo("42");
    }

    @Test
    @DisplayName("QUIT/RETURN carries values that look like nothing")
    void quitReturnCarriesDegenerateValues() {
        assertThat(AnInterpreter.create().display(runAQuit("quit/return 0"))).isEqualTo("0");
        assertThat(AnInterpreter.create().display(runAQuit("quit/return \"\"")))
                .isEqualTo("\"\"");
        assertThat(AnInterpreter.create().display(runAQuit("quit/return []")))
                .isEqualTo("[]");
        assertThat(AnInterpreter.create().display(runAQuit("quit/return false")))
                .isEqualTo("#(false)");
    }

    @Test
    @DisplayName("QUIT/RETURN none is still a quit, and carries unset as a bare QUIT does")
    void quitReturnNoneIsStillAQuit() {
        ScriptOutcome outcome = runAQuit("quit/return none");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(outcome.value().datatype().literalSpelling()).isEqualTo("unset!");
    }

    @Test
    @DisplayName("QUIT inside a function ends the whole run, not the function")
    void quitFromInsideAFunctionEndsEverything() {
        ScriptOutcome outcome = runAQuit(
                "f: func [] [quit/return 1] f 99");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(AnInterpreter.create().display(outcome))
                .as("the 99 after the call must never be reached")
                .isEqualTo("1");
    }

    @Test
    @DisplayName("QUIT inside a loop ends the whole run")
    void quitFromInsideALoopEndsEverything() {
        ScriptOutcome outcome = runAQuit("repeat n 10 [if n = 3 [quit/return n]] 99");

        assertThat(AnInterpreter.create().display(outcome)).isEqualTo("3");
    }

    @Test
    @DisplayName("TRY does not catch a QUIT, because a QUIT is not a failure")
    void tryDoesNotCatchAQuit() {
        ScriptOutcome outcome = runAQuit("try [quit/return 5] 99");

        assertThat(outcome.conclusion())
                .as("TRY catches errors; stopping on purpose is not one")
                .isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(AnInterpreter.create().display(outcome)).isEqualTo("5");
    }

    @Test
    @DisplayName("CATCH does not catch a QUIT either")
    void catchDoesNotCatchAQuit() {
        ScriptOutcome outcome = runAQuit("catch [quit/return 5] 99");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
        assertThat(AnInterpreter.create().display(outcome)).isEqualTo("5");
    }

    @Test
    @DisplayName("QUIT/NOW is accepted and behaves the same")
    void quitNowIsAccepted() {
        ScriptOutcome outcome = runAQuit("quit/now");

        assertThat(outcome.conclusion()).isEqualTo(Conclusion.QUIT_EARLY);
    }

    @Test
    @DisplayName("the interpreter still works after a script quits")
    void letsTheHostSurviveAQuit() {
        Interpreter interpreter = AnInterpreter.create();
        interpreter.defineNewWordsIn("kept: 7 quit/return 1");
        interpreter.run("kept: 7 quit/return 1");

        assertThat(interpreter.display(interpreter.run("kept + 1")))
                .as("a guest stopping must not take the host with it")
                .isEqualTo("8");
    }

    @Test
    @DisplayName("QUIT can be taken as a value, which is how Rebol's own library uses it")
    void quitCanBeAliased() {
        Interpreter interpreter = AnInterpreter.create();

        assertThat(interpreter.display(interpreter.run("q: :quit type? :q")))
                .as("base-constants.reb writes `q: :quit` and stopped there without this")
                .isEqualTo("#(native!)");
    }
}
