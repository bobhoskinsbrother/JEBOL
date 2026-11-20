package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("opens-a-real-window")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(120)
class TextMeasuredAsDrawnOnTheDesktopEndToEndTest {

    private static final String CARET_AFTER_THE_TEXT_AND_WHERE_ITS_INK_ENDS = """
            shown: {%s}
            plain: make gob! [size: 600x40 text: shown]
            caret: caret-to-offset plain 1 1 + length? shown
            picture: to image! plain
            inked: 0
            repeat across picture/size/x [
                repeat down picture/size/y [
                    seen: pick picture as-pair across down
                    if 230 > min min seen/1 seen/2 seen/3 [inked: across]
                ]
            ]
            to integer! caret/x - inked
            """;

    private int answer(String source) {
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(DesktopScreen.onThisMachine(new ClasspathFiles())),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS).withTimeLimit(Duration.ofSeconds(60)));
        interpreter.defineNewWordsIn(source);
        return Integer.parseInt(interpreter.display(interpreter.run(source)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "The quick brown fox jumps over the lazy dog",
            "AVATAR To Wa Yo fi fl office",
            "iiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiiii",
            "a😀b",
            "😀😃😄"})
    void theCaretAfterTheTextIsWhereItsInkEnds(String shown) {
        assertThat(answer(CARET_AFTER_THE_TEXT_AND_WHERE_ITS_INK_ENDS.formatted(shown))).isBetween(-1, 2);
    }
}
