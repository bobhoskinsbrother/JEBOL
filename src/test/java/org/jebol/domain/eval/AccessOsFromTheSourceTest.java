package org.jebol.domain.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ACCESS-OS, which asked the operating system five things and JEBOL refused
 * all five.
 *
 * <p>It was declared with one argument and no refinement against R3's two and
 * one, so {@code access-os/set 'pid [id signal]} could not be written here at
 * all, and {@code access-os 'pid} answered a refusal where a real Rebol
 * answers a number.
 *
 * <p>Four of the five fields stay refused, and refused is the C's own answer
 * for them: {@code n-io.c} maps a platform with no such call to {@code
 * OS_ENA}, which raises {@code not-here} naming the field. The JVM has a
 * process id and has no portable user or group id, so {@code pid} is answered
 * and {@code uid}, {@code euid}, {@code gid} and {@code egid} take that path.
 * A real Rebol on this machine answers all five, and that is the remaining
 * difference.
 */
class AccessOsFromTheSourceTest {

    private static String answerTo(String source) {
        return answerFrom(Interpreter.create(), source);
    }

    private static String answerAllowingProcesses(String source) {
        return answerFrom(Interpreter.withBounds(
                Bounds.standard().granting(HostService.PROCESSES)), source);
    }

    private static String answerFrom(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String whatHappensTo(String call) {
        return answerTo("either error? e: try [" + call + "] [e/id] ['accepted]");
    }

    @Nested
    @DisplayName("reading a field")
    class Reading {

        @Test
        @DisplayName("the process id is a whole number, and it is this process's")
        void theProcessIdIsAnswered() {
            assertThat(answerTo("integer? access-os 'pid")).isEqualTo("#(true)");
            assertThat(answerTo("access-os 'pid"))
                    .isEqualTo(String.valueOf(ProcessHandle.current().pid()));
        }

        @Test
        @DisplayName("and the four identity fields say they are not here")
        void theidentityFieldsSayNotHere() {
            for (String field : new String[] {"uid", "euid", "gid", "egid"}) {
                assertThat(whatHappensTo("access-os '" + field))
                        .as("n-io.c raises not-here where the platform has no such "
                                + "call, and the JVM has none of these")
                        .isEqualTo("not-here");
            }
        }

        @Test
        @DisplayName("a word that is not one of the five is an invalid argument")
        void anunknownFieldIsInvalid() {
            assertThat(whatHappensTo("access-os 'nope"))
                    .as("the C reaches Trap_Arg on the field, not on the declaration")
                    .isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("and the field must be a word")
        void thefieldMustBeAWord() {
            assertThat(whatHappensTo("access-os {pid}")).isEqualTo("expect-arg");
            assertThat(whatHappensTo("access-os 1")).isEqualTo("expect-arg");
        }
    }

    @Nested
    @DisplayName("setting a field, which for a pid means sending it a signal")
    class Setting {

        @Test
        @DisplayName("the value may be a whole number or a block, and nothing else")
        void thevalueIsDeclaredTwoWays() {
            assertThat(whatHappensTo("access-os/set 'pid {x}"))
                    .as("the declaration turns a string away before the body runs")
                    .isEqualTo("expect-arg");
            assertThat(whatHappensTo("access-os/set 'pid 1.5")).isEqualTo("expect-arg");
        }

        @Test
        @DisplayName("a block must hold exactly a pid and a signal, both whole numbers")
        void ablockIsAPidAndASignal() {
            assertThat(whatHappensTo("access-os/set 'pid [1]"))
                    .as("VAL_LEN(val) != 2 is Trap_Arg on the block itself")
                    .isEqualTo("invalid-arg");
            assertThat(whatHappensTo("access-os/set 'pid [1 2 3]")).isEqualTo("invalid-arg");
            assertThat(whatHappensTo("access-os/set 'pid [{a} 2]")).isEqualTo("invalid-arg");
            assertThat(whatHappensTo("access-os/set 'pid [1 {b}]")).isEqualTo("invalid-arg");
        }

        @Test
        @DisplayName("setting an identity field is still not here")
        void settinganIdentityFieldIsNotHere() {
            assertThat(whatHappensTo("access-os/set 'uid 0")).isEqualTo("not-here");
        }

        @Test
        @DisplayName("signalling a process a script was not granted is refused")
        void withoutTheGrantSignallingIsRefused() {
            assertThat(whatHappensTo("access-os/set 'pid 1"))
                    .as("ending another program is starting one's twin, and the "
                            + "host grants the pair together")
                    .isEqualTo("no-service");
        }

        @Test
        @DisplayName("and a process nobody may signal answers permission-denied")
        void signallingWhatCannotBeSignalledIsDenied() {
            assertThat(answerAllowingProcesses(
                    "either error? e: try [access-os/set 'pid 1] [e/id] ['accepted]"))
                    .as("process 1 belongs to the system on every platform this runs "
                            + "on, and a real Rebol says the same word here")
                    .isEqualTo("permission-denied");
        }
    }
}
