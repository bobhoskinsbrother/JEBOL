package org.jebol.application;

import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.PausePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AWaitsTimeIsPassedByTheHostTest {

    private static final class ARecordedPause implements PausePort {

        private final List<Long> askedFor = new ArrayList<>();
        private final boolean letsItPass;

        ARecordedPause(boolean letsItPass) {
            this.letsItPass = letsItPass;
        }

        @Override
        public boolean pauseFor(long milliseconds) {
            askedFor.add(milliseconds);
            return letsItPass;
        }
    }

    private static Interpreter makeAnInterpreterWithThePause(ARecordedPause pause) {
        return Interpreter.createOn(
                Interpreter.standardPorts().withPause(pause),
                OutputPort.discard(),
                Bounds.standard());
    }

    @Test
    @DisplayName("a wait asks the host for its whole time, a slice at a time")
    void asksForTheWholeTimeASliceAtATime() {
        ARecordedPause pause = new ARecordedPause(true);
        Interpreter interpreter = makeAnInterpreterWithThePause(pause);

        String answer = interpreter.display(interpreter.run("wait 0.25"));

        assertThat(pause.askedFor).containsExactly(50L, 50L, 50L, 50L, 50L);
        assertThat(answer).isEqualTo("_");
    }

    @Test
    @DisplayName("a slice is never longer than fifty milliseconds")
    void keepsEverySliceWithinFiftyMilliseconds() {
        ARecordedPause pause = new ARecordedPause(true);
        Interpreter interpreter = makeAnInterpreterWithThePause(pause);

        interpreter.run("wait 0.07");

        assertThat(pause.askedFor).containsExactly(50L, 20L);
    }

    @Test
    @DisplayName("a host that will not let the time pass ends the wait there")
    void endsTheWaitWhenAPauseDoesNotHappen() {
        ARecordedPause pause = new ARecordedPause(false);
        Interpreter interpreter = makeAnInterpreterWithThePause(pause);

        String answer = interpreter.display(interpreter.run("wait 1"));

        assertThat(pause.askedFor).containsExactly(50L);
        assertThat(answer).isEqualTo("_");
    }

    @Test
    @Timeout(20)
    @DisplayName("an interpreter given no pause still lets the time pass on its own thread")
    void letsTheTimePassWithTheBuiltInPause() {
        Interpreter interpreter = Interpreter.create();

        long started = System.nanoTime();
        interpreter.run("wait 0.2");
        long took = System.nanoTime() - started;

        assertThat(took)
                .as("a wait that did not stop for its time would finish at once")
                .isGreaterThanOrEqualTo(Duration.ofMillis(150).toNanos());
    }
}
