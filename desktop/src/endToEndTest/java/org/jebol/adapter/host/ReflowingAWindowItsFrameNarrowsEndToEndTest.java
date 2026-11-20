package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("opens-a-real-window")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(120)
class ReflowingAWindowItsFrameNarrowsEndToEndTest {

    private static final String TITLE = "JEBOL reflowing a window";
    private static final Duration PATIENCE = Duration.ofSeconds(20);
    private static final int NARROWED_TO = 300;

    private static final String OPEN_THE_SURVEY = """
            faces-within?: func [window wide /local fits walk] [
                fits: true
                walk: func [gob at] [
                    foreach sub any [gob/pane []] [
                        if all [
                            object? sub/data
                            sub = select sub/data 'gob
                            (at/x + sub/offset/x + sub/size/x) > (wide + 1)
                        ] [fits: false]
                        walk sub at + sub/offset
                    ]
                ]
                walk window 0x0
                fits
            ]
            win: view/no-wait/options [
                title "Opinion Survey"
                text "Do you want programs to be easy to build?"
                panel 2 [
                    label "Answer:"
                    group [radio "Agree" radio "Disagree" radio "Not sure"]
                    label "Name:"
                    field
                    label "Comment:"
                    area
                ]
            ] [title: "%s"]""".formatted(TITLE);

    private Interpreter interpreter;

    @AfterEach
    void closeTheWindow() {
        if (interpreter != null) {
            run("unview win");
        }
    }

    private String run(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private static JFrame findTheFrame() {
        return Arrays.stream(Frame.getFrames())
                .filter(JFrame.class::isInstance)
                .map(JFrame.class::cast)
                .filter(each -> TITLE.equals(each.getTitle()) && each.isDisplayable())
                .findFirst()
                .orElseThrow();
    }

    private static void onTheToolkit(Runnable work) {
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        } catch (InvocationTargetException failed) {
            throw new IllegalStateException(failed.getCause());
        }
    }

    private void takeEventsUntil(String hoped, BooleanSupplier arrived) {
        long giveUpAt = System.nanoTime() + PATIENCE.toNanos();
        while (!arrived.getAsBoolean()) {
            assertThat(System.nanoTime()).as("still waiting for %s after %s; the window is %s",
                    hoped, PATIENCE, run("win/size")).isLessThan(giveUpAt);
            run("wait [gui-event-port 0]");
            try {
                Thread.sleep(50);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(stopped);
            }
        }
    }

    @Test
    @DisplayName("a GUI window whose frame its user narrows reflows every face inside the new width")
    void reflowsWhenItsFrameNarrows() {
        interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(DesktopScreen.onThisMachine(new ClasspathFiles())),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS).withTimeLimit(Duration.ofSeconds(100)));
        run(OPEN_THE_SURVEY);
        JFrame frame = findTheFrame();
        takeEventsUntil("the window to show", frame::isShowing);
        assertThat(run("win/size/x > " + NARROWED_TO)).isEqualTo("#(true)");

        onTheToolkit(() -> {
            int chrome = frame.getWidth() - frame.getContentPane().getWidth();
            frame.setSize(NARROWED_TO + chrome, frame.getHeight());
        });

        takeEventsUntil("the window to be " + NARROWED_TO + " wide",
                () -> run("win/size/x").equals(NARROWED_TO + ".0"));
        takeEventsUntil("every face to fit", () -> run("faces-within? win " + NARROWED_TO).equals("#(true)"));
    }
}
