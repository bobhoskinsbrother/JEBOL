package org.jebol.application;

import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(300)
class AScriptPastTheMemoryCeilingTest {

    private static final long ROOM_ABOVE_WHAT_IS_HELD = 48L * 1024 * 1024;

    private static String run(Interpreter interpreter, String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static Interpreter withCeiling(long ceiling) {
        return Interpreter.createOn(PlatformUnderTest.current().ports(), OutputPort.discard(),
                AnInterpreter.stretchForThePlatform(Bounds.standard()
                        .withTimeLimit(Duration.ofSeconds(60))
                        .withMemoryCeiling(ceiling)));
    }

    private static long heldNow() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @Test
    @DisplayName("a script that grows past the ceiling gets REBOL's own no-memory error, and the interpreter goes on")
    void growingPastTheCeilingIsNoMemory() {
        Interpreter interpreter = withCeiling(heldNow() + ROOM_ABOVE_WHAT_IS_HELD);

        assertThat(run(interpreter, """
                held: copy []
                e: try [forever [append/only held array/initial 131072 0]]
                all [error? e e/type = 'internal e/id = 'no-memory]
                """)).isEqualTo("#(true)");
        assertThat(run(interpreter, "(length? held) > 8")).isEqualTo("#(true)");
        assertThat(run(interpreter, "held: none 1 + 2")).isEqualTo("3");
    }

    @Test
    @DisplayName("a script that makes garbage well past the ceiling, holding little, is never stopped")
    void garbageIsNotHeld() {
        Interpreter interpreter = withCeiling(heldNow() + ROOM_ABOVE_WHAT_IS_HELD);

        assertThat(run(interpreter, """
                loop 200 [array/initial 131072 0]
                true
                """)).isEqualTo("#(true)");
    }

    @Test
    @DisplayName("without a ceiling, the bounds hold none")
    void standardBoundsHoldNone() {
        assertThat(Bounds.standard().hasMemoryCeiling()).isFalse();
        assertThat(Bounds.standard().withMemoryCeiling(1024).hasMemoryCeiling()).isTrue();
    }
}
