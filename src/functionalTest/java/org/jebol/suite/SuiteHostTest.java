package org.jebol.suite;

import org.jebol.application.Interpreter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
