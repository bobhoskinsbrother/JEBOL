package org.jebol.android.functional;

import org.jebol.adapter.android.AndroidHost;
import org.jebol.android.InterpreterThreads;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TheInterpreterThreadHasRoomForDeepCallsTest {

    private static String answerOnInterpreterThread(String source) throws InterruptedException {
        AtomicReference<String> answer = new AtomicReference<>();
        Thread interpreting = InterpreterThreads.start("jebol-deep-calls", () -> {
            Interpreter interpreter = Interpreter.createOn(
                    new AndroidHost().ports(), OutputPort.discard(), Bounds.standard());
            interpreter.defineNewWordsIn(source);
            answer.set(interpreter.display(interpreter.run(source)));
        });
        interpreting.join();
        return answer.get();
    }

    @Test
    @DisplayName("the thread the app interprets on runs two thousand calls deep, each reading its own argument")
    void runsTwoThousandCallsDeep() throws InterruptedException {
        assertThat(answerOnInterpreterThread("""
                dp: func [x] [either x = 0 [0] [1 + dp x - 1]]
                dp 2000""")).isEqualTo("2000");
    }
}
