package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That a measuring tool answers what the gate answers.
 *
 * <p>The tools that report on the suite are only worth their output if they run
 * a file the way the gate runs it. Two of them did not: they granted every host
 * service and installed only a filesystem, so a suite file asking for the
 * environment or for a process was told it had none. That is not a smaller host
 * but a differently wrong one, and it is worse than no tool, because it reports
 * a blocker where the gate sees none and the difference is invisible in the
 * output.
 *
 * <p>Four pieces of work in {@code goals.md} were written from those phantom
 * stops. The cost was not the tools; it was believing them.
 *
 * <p>So this asks the three things granting alone does not give, through the
 * interpreter every one of them now builds.
 */
class SuiteHostTest {

    private static String answerTo(String source) {
        Interpreter interpreter = SuiteHost.installOn(
                Interpreter.withBounds(SuiteHost.grantingEverything()));
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdFrom(String source) {
        return answerTo("failure: try [" + source + "] "
                + "either error? failure [failure/id] ['no-error]");
    }

    @Test
    @DisplayName("the environment can be read, rather than refused")
    void theEnvironmentCanBeRead() {
        assertThat(errorIdFrom("get-env \"PATH\""))
                .as("a suite file reads PWD and HOME; answering 'no environment' "
                        + "makes a stop the gate never sees")
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("and listed")
    void theEnvironmentCanBeListed() {
        assertThat(errorIdFrom("list-env")).isEqualTo("no-error");
    }

    @Test
    @DisplayName("a process can be started, rather than refused")
    void aprocessCanBeStarted() {
        assertThat(errorIdFrom("call/shell/wait \"exit 0\""))
                .as("evaluation-test and module-test shell out; 'no way to start "
                        + "a program' was this tool's answer, never the gate's")
                .isEqualTo("no-error");
    }

    @Test
    @DisplayName("the suite's own data files are where the suite looks for them")
    void thedataFilesAreThere() {
        assertThat(answerTo("exists? %units/files/quit.r3"))
                .isEqualTo("file");
    }

    @Test
    @DisplayName("and a script can be run from one, which needs all of it at once")
    void ascriptRunsFromAdataFile() {
        assertThat(answerTo("42 = do %units/files/quit-return.r3"))
                .isEqualTo("#(true)");
    }
}
