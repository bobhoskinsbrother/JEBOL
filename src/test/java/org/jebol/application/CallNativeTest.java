package org.jebol.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jebol.domain.eval.ProcessPort;
import org.jebol.domain.host.HostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CALL starts another program.
 *
 * <p>Specified in {@code spec/embed.allium}.
 *
 * <p>A stand-in port records what it was asked for, thus these tests start
 * no program and do not depend on the machine. What the JDK does with a
 * command belongs to the adapter, and what the native asks for belongs
 * here.
 */
class CallNativeTest {

    /** A port that records the last request and answers a fixed result. */
    private static final class Recorded implements ProcessPort {

        private List<String> command;
        private boolean throughShell;
        private boolean waited;

        @Override
        public Finished runAndWait(List<String> asked, boolean shell) {
            this.command = asked;
            this.throughShell = shell;
            this.waited = true;
            return new Finished(3, "what it printed", "");
        }

        @Override
        public long start(List<String> asked, boolean shell) {
            this.command = asked;
            this.throughShell = shell;
            this.waited = false;
            return 4242;
        }
    }

    private static Interpreter reaching(boolean granted, Recorded port) {
        Bounds bounds = granted
                ? Bounds.standard().granting(HostService.PROCESSES)
                : Bounds.standard();
        Interpreter interpreter = Interpreter.withBounds(bounds);
        interpreter.useProcesses(port);
        return interpreter;
    }

    private static String answerTo(Interpreter interpreter, String source) {
        interpreter.defineFreshWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static String errorIdOf(Interpreter interpreter, String source) {
        return answerTo(interpreter,
                "e: try [" + source + "] either error? e [e/id] ['no-error]");
    }

    @Test
    @DisplayName("a block is the program and its arguments, already separated")
    void aBlockIsTakenAsItStands() {
        Recorded port = new Recorded();
        answerTo(reaching(true, port), "call [\"ls\" \"-l\"]");
        assertThat(port.command).containsExactly("ls", "-l");
    }

    @Test
    @DisplayName("a block does not go through a shell")
    void aBlockNeedsNoShell() {
        // The safe form. A shell reads its command as text, thus anything
        // the script put in the command becomes part of it.
        Recorded port = new Recorded();
        answerTo(reaching(true, port), "call [\"ls\"]");
        assertThat(port.throughShell).isFalse();
    }

    @Test
    @DisplayName("a string goes through a shell as one word")
    void aStringIsShellText() {
        Recorded port = new Recorded();
        answerTo(reaching(true, port), "call \"ls -l\"");
        assertThat(port.command).containsExactly("ls -l");
        assertThat(port.throughShell).isTrue();
    }

    @Test
    @DisplayName("/SHELL makes a block go through the shell too")
    void theShellCanBeAskedFor() {
        Recorded port = new Recorded();
        answerTo(reaching(true, port), "call/shell [\"ls\"]");
        assertThat(port.throughShell).isTrue();
    }

    @Test
    @DisplayName("without /WAIT the answer is the number of the new process")
    void notWaitingAnswersTheNumber() {
        assertThat(answerTo(reaching(true, new Recorded()), "call [\"ls\"]"))
                .isEqualTo("4242");
    }

    @Test
    @DisplayName("/WAIT answers the exit code")
    void waitingAnswersTheCode() {
        assertThat(answerTo(reaching(true, new Recorded()), "call/wait [\"ls\"]"))
                .isEqualTo("3");
    }

    @Test
    @DisplayName("/OUTPUT answers what the program wrote, and waits")
    void outputImpliesWaiting() {
        Recorded port = new Recorded();
        assertThat(answerTo(reaching(true, port), "call/output [\"ls\"]"))
                .isEqualTo("\"what it printed\"");
        assertThat(port.waited).isTrue();
    }

    @Test
    @DisplayName("without the grant CALL is refused")
    void theGrantIsNeeded() {
        assertThat(errorIdOf(reaching(false, new Recorded()), "call [\"ls\"]"))
                .isEqualTo("no-service");
    }

    @Test
    @DisplayName("with the grant and no port, CALL still fails")
    void aPortIsAlsoNeeded() {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.PROCESSES));
        assertThat(errorIdOf(interpreter, "call [\"ls\"]")).isEqualTo("no-port");
    }
}
