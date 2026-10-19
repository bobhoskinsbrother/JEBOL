package org.jebol.application;

import org.jebol.adapter.host.JavaProcess;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EndingAProcessThatIsNotThereFromTheSourceTest {

    private static final String A_NUMBER_NO_PROCESS_IS_RUNNING_UNDER = "2147483646";

    private static Interpreter granted() {
        Interpreter interpreter = Interpreter.withBounds(Bounds.standard().grant(HostService.PROCESSES));
        interpreter.useProcesses(new JavaProcess());
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String failureOf(String source) {
        return failureOf(granted(), source);
    }

    private static String failureOf(Interpreter interpreter, String source) {
        return answerTo(interpreter, "e: try [" + source + """
                ]
                either error? e [reduce [e/type e/id e/arg1]] [reduce ['ok]]""");
    }

    @Test
    @DisplayName("a number no process is running under names itself, not a permission")
    void namesANumberNoProcessIsRunningUnder() {
        assertThat(failureOf("access-os/set 'pid " + A_NUMBER_NO_PROCESS_IS_RUNNING_UNDER))
                .isEqualTo("[Access process-not-found " + A_NUMBER_NO_PROCESS_IS_RUNNING_UNDER + "]");
    }

    @Test
    @DisplayName("and a signal named beside the number answers the same way")
    void answersTheSameWayWithASignalNamed() {
        assertThat(failureOf(
                "access-os/set 'pid [" + A_NUMBER_NO_PROCESS_IS_RUNNING_UNDER + " 9]"))
                .isEqualTo("[Access process-not-found " + A_NUMBER_NO_PROCESS_IS_RUNNING_UNDER + "]");
    }

    @Test
    @DisplayName("reading the pid rather than setting one answers this process")
    void answersThisProcessForThePid() {
        assertThat(answerTo(granted(), """
                pid: access-os 'pid
                all [integer? pid  pid > 0]""")).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("a field this machine does not answer is not-here, which is a different thing again")
    void saysNotHereForAFieldThisMachineDoesNotAnswer() {
        Interpreter withoutProcessPort = Interpreter.withBounds(Bounds.standard().grant(HostService.PROCESSES));

        assertThat(failureOf(withoutProcessPort, """
                access-os 'uid""")).startsWith("[Internal not-here");
    }

    @Test
    @DisplayName("and a field the operating system has no name for at all is a bad argument")
    void refusesAFieldTheOperatingSystemHasNoNameFor() {
        assertThat(failureOf("""
                access-os 'nosuchfield""")).startsWith("[Script invalid-arg");
    }

    @Test
    @DisplayName("without the processes service nothing is signalled at all")
    void signalsNothingWithoutTheProcessesService() {
        assertThat(answerTo(Interpreter.create(), """
                e: try [access-os/set 'pid 2147483646]
                either error? e [e/id] ['ok]""")).isEqualTo("no-service");
    }
}
