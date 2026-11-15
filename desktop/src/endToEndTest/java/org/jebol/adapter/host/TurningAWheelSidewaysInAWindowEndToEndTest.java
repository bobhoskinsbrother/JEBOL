package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import javax.swing.JFrame;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("opens-a-real-window")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(120)
class TurningAWheelSidewaysInAWindowEndToEndTest {

    private static final String TITLE = "JEBOL turning a wheel sideways";
    private static final Duration PATIENCE = Duration.ofSeconds(15);
    private static final int ACROSS = 40;
    private static final int DOWN = 30;
    private static final int TOWARD_THE_TOP = -1;

    private static final String OPEN_THE_WINDOW = """
            go 'a go 'b
            win: view/no-wait/options make gob! [size: 320x200 color: 10.20.30] [title: "%s"]
            seen: copy []
            handle-events [
                name: 'watcher
                priority: 90
                handler: func [event] [
                    if find [down up scroll-line scroll-page back forward] event/type [append seen event/type]
                    event
                ]
            ]""".formatted(TITLE);

    private Interpreter interpreter;
    private JFrame frame;
    private Component drawable;

    @BeforeEach
    void openTheWindow() {
        interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(DesktopScreen.onThisMachine(new ClasspathFiles())),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS).grant(HostService.NAVIGATION)
                        .withTimeLimit(Duration.ofSeconds(100)));
        run(OPEN_THE_WINDOW);
        frame = findTheFrame();
        drawable = frame.getContentPane();
        takeEventsUntil("the window showing", () -> frame.isShowing());
        run("clear seen");
    }

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

    private void takeEventsUntil(String awaited, BooleanSupplier arrived) {
        long giveUpAt = System.nanoTime() + PATIENCE.toNanos();
        while (!arrived.getAsBoolean()) {
            assertThat(System.nanoTime())
                    .as("still waiting for %s after %s, having heard %s", awaited, PATIENCE, run("seen"))
                    .isLessThan(giveUpAt);
            run("wait [system/ports/event 0]");
            pause();
        }
    }

    private static void pause() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        }
    }

    private void post(AWTEvent event) {
        EventQueue queue = Toolkit.getDefaultToolkit().getSystemEventQueue();
        queue.postEvent(event);
    }

    private MouseWheelEvent wheelTurn(int modifiersEx) {
        return new MouseWheelEvent(drawable, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), modifiersEx,
                ACROSS, DOWN, 0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, TOWARD_THE_TOP);
    }

    private MouseEvent button(int id, int button) {
        return new MouseEvent(drawable, id, System.currentTimeMillis(), 0, ACROSS, DOWN, 1, false, button);
    }

    private void takeEventsUntilALineIsHeard() {
        takeEventsUntil("the plain turn's scroll-line", () -> run("seen").contains("scroll-line"));
    }

    @Test
    @DisplayName("a turn Java flags with Shift, which is how it hands over a sideways turn, reaches the script as nothing")
    void aSidewaysTurnIsNothing() {
        post(wheelTurn(InputEvent.SHIFT_DOWN_MASK));
        post(wheelTurn(InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
        post(wheelTurn(0));

        takeEventsUntilALineIsHeard();

        assertThat(run("seen")).isEqualTo("[scroll-line]");
    }

    @Test
    @DisplayName("on Linux, buttons four and five are a sideways turn: no down, no up, and the history stays")
    void onLinuxFourAndFiveAreNothing() {
        assumeTrue(DesktopSystem.named(System.getProperty("os.name", "")) == DesktopSystem.X11,
                "Java numbers a sideways turn as buttons four and five only on X11");
        for (int sideways : new int[] {4, 5}) {
            post(button(MouseEvent.MOUSE_PRESSED, sideways));
            post(button(MouseEvent.MOUSE_RELEASED, sideways));
        }
        post(wheelTurn(0));

        takeEventsUntilALineIsHeard();

        assertThat(run("seen")).isEqualTo("[scroll-line]");
        assertThat(run("current-place")).isEqualTo("[b]");
    }

    @Test
    @DisplayName("on Linux, button six is the mouse's back: a down, an up, and a move back")
    void onLinuxSixIsBack() {
        assumeTrue(DesktopSystem.named(System.getProperty("os.name", "")) == DesktopSystem.X11,
                "Java numbers the back button six only on X11");
        post(button(MouseEvent.MOUSE_PRESSED, 6));
        post(button(MouseEvent.MOUSE_RELEASED, 6));

        takeEventsUntil("the move back", () -> run("seen").contains("back"));
        takeEventsUntil("the release", () -> run("seen").contains("up"));

        assertThat(run("sort copy seen")).isEqualTo("[back down up]");
        assertThat(run("current-place")).isEqualTo("[a]");
    }
}
