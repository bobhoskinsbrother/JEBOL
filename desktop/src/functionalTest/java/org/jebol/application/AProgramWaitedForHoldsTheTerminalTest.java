package org.jebol.application;

import org.jebol.adapter.host.JavaProcess;
import org.jebol.adapter.host.TerminalLending;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class AProgramWaitedForHoldsTheTerminalTest {

    private final List<String> lent = new ArrayList<>();

    private final TerminalLending recording = new TerminalLending() {
        @Override
        public <T> T lendDuring(Supplier<T> program) {
            lent.add("lent");
            T answered = program.get();
            lent.add("back");
            return answered;
        }
    };

    private String answerTo(String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withProcesses(new JavaProcess(recording)),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.PROCESSES));
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    @Test
    @DisplayName("CALL/WAIT lends the terminal to the program for as long as it runs, and takes it back")
    void lendsTheTerminalWhileItWaits() {
        assertThat(answerTo("call/wait/shell {exit 3}")).isEqualTo("3");
        assertThat(lent).containsExactly("lent", "back");
    }

    @Test
    @DisplayName("CALL without /WAIT lends nothing, since nothing waits for the program")
    void lendsNothingWithoutWaiting() {
        answerTo("call/shell {exit 0}");

        assertThat(lent).isEmpty();
    }
}
