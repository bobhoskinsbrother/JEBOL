package org.jebol.domain.eval;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.PlatformUnderTest;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.testing.RunsOn;
import org.jebol.testing.TestTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jebol.testing.OnEachTarget.elsewhere;
import static org.jebol.testing.OnEachTarget.expected;
import static org.jebol.testing.OnEachTarget.on;

class AccessOsFromTheSourceTest {

    private static String answerTo(String source) {
        return answerFrom(AnInterpreter.create(), source);
    }

    private static String answerAllowingProcesses(String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withProcesses(PlatformUnderTest.current().processes()),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.PROCESSES));
        return answerFrom(interpreter, source);
    }

    private static String answerFrom(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String describeWhatHappensTo(String call) {
        return answerTo("either error? e: try [" + call + "] [e/id] ['accepted]");
    }

    @Nested
    @DisplayName("reading a field")
    @RunsOn(value = {TestTarget.DESKTOP_LINUX, TestTarget.DESKTOP_MAC, TestTarget.DESKTOP_WINDOWS},
            because = "it compares the process number with ProcessHandle, which Android does not have")
    class Reading {

        @Test
        @DisplayName("the process id is a whole number, and it is this process's")
        void answersTheProcessId() {
            assertThat(answerTo("integer? access-os 'pid")).isEqualTo("#(true)");
            assertThat(answerTo("access-os 'pid"))
                    .isEqualTo(String.valueOf(ProcessHandle.current().pid()));
        }

        @Test
        @DisplayName("and the four identity fields say they are not here")
        void theidentityFieldsSayNotHere() {
            for (String field : new String[] {"uid", "euid", "gid", "egid"}) {
                assertThat(describeWhatHappensTo("access-os '" + field))
                        .as("n-io.c raises not-here where the platform has no such "
                                + "call, and the JVM has none of these")
                        .isEqualTo("not-here");
            }
        }

        @Test
        @DisplayName("a word that is not one of the five is an invalid argument")
        void anunknownFieldIsInvalid() {
            assertThat(describeWhatHappensTo("access-os 'nope"))
                    .as("the C reaches Trap_Arg on the field, not on the declaration")
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and the field must be a word")
        void thefieldMustBeAWord() {
            assertThat(describeWhatHappensTo("access-os {pid}")).isEqualTo("expect-arg");
            assertThat(describeWhatHappensTo("access-os 1")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("setting a field, which for a pid means sending it a signal")
    class Setting {

        @Test
        @DisplayName("the value may be a whole number or a block, and nothing else")
        void thevalueIsDeclaredTwoWays() {
            assertThat(describeWhatHappensTo("access-os/set 'pid {x}"))
                    .as("the declaration turns a string away before the body runs")
                    .isEqualTo("expect-arg");
            assertThat(describeWhatHappensTo("access-os/set 'pid 1.5")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("a block must hold exactly a pid and a signal, both whole numbers")
        void ablockIsAPidAndASignal() {
            assertThat(describeWhatHappensTo("access-os/set 'pid [1]"))
                    .as("VAL_LEN(val) != 2 is Trap_Arg on the block itself")
                    .isEqualTo("invalid-arg");
            assertThat(describeWhatHappensTo("access-os/set 'pid [1 2 3]")).isEqualTo("invalid-arg");
            assertThat(describeWhatHappensTo("access-os/set 'pid [{a} 2]")).isEqualTo("invalid-arg");
            assertThat(describeWhatHappensTo("access-os/set 'pid [1 {b}]")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("setting an identity field is still not here")
        void settinganIdentityFieldIsNotHere() {
            assertThat(describeWhatHappensTo("access-os/set 'uid 0")).isEqualTo("not-here");
        }

        @Test
        @DisplayName("signalling a process a script was not granted is refused")
        void refusesSignallingWithoutTheGrant() {
            assertThat(describeWhatHappensTo("access-os/set 'pid 1"))
                    .as("ending another program is starting one's twin, and the "
                            + "host grants the pair together")
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("and the system's own process, 4 on Windows and 1 elsewhere, nobody may signal: permission-denied, as a real Rebol says")
        @RunsOn(value = {TestTarget.DESKTOP_LINUX, TestTarget.DESKTOP_MAC, TestTarget.DESKTOP_WINDOWS},
                because = "it signals a process, and an Android app starts none to signal: its process port refuses as a page's does (decision 77)")
        void deniesSignallingWhatCannotBeSignalled() {
            String systemsOwn = expected(on(TestTarget.DESKTOP_WINDOWS, "4"), elsewhere("1"));

            assertThat(answerAllowingProcesses("""
                    either error? e: try [access-os/set 'pid %s] [e/id] ['accepted]""".formatted(systemsOwn)))
                    .isEqualTo("permission-denied");
        }
    }
}
