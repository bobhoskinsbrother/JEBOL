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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("opens-a-real-window")
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(value = 120, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@ExtendWith(ThreadsShownOnTimeout.class)
class ArrangingAWindowEndToEndTest {

    private static final String TITLE = "JEBOL arranging a window";
    private static final Duration PATIENCE = Duration.ofSeconds(15);

    private static final int DRAINS_THAT_MAKE_A_QUIET_HALF_SECOND = 10;

    private static final String OPEN_THE_WINDOW = """
            win: view/no-wait/options make gob! [size: 320x200 color: 10.20.30] [title: "%s"]
            seen: copy []
            handle-events [
                name: 'watcher
                priority: 90
                handler: func [event] [
                    if find [minimize maximize restore resize] event/type [append seen event/type]
                    event
                ]
            ]""".formatted(TITLE);

    private Interpreter interpreter;
    private JFrame frame;

    @BeforeEach
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void openTheWindow() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        assertThat(toolkit.isFrameStateSupported(Frame.ICONIFIED))
                .as("this desktop can minimise a window").isTrue();
        assertThat(toolkit.isFrameStateSupported(Frame.MAXIMIZED_BOTH))
                .as("this desktop can maximise a window").isTrue();
        interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(DesktopScreen.onThisMachine(new ClasspathFiles())),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS).withTimeLimit(Duration.ofSeconds(100)));
        run(OPEN_THE_WINDOW);
        frame = findTheFrame();
        takeEventsUntil(() -> frame.isShowing() && run("seen").contains("resize"));
        waitUntilTheWindowManagerHasSettledIt();
    }

    private void waitUntilTheWindowManagerHasSettledIt() {
        long giveUpAt = System.nanoTime() + PATIENCE.toNanos();
        int quietDrains = 0;
        while (quietDrains < DRAINS_THAT_MAKE_A_QUIET_HALF_SECOND) {
            assertThat(System.nanoTime())
                    .as("the window manager was still moving the new window after %s", PATIENCE)
                    .isLessThan(giveUpAt);
            run("clear seen");
            takeEventsForAMoment();
            quietDrains = run("seen").equals("[]") ? quietDrains + 1 : 0;
        }
    }

    @AfterEach
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void closeTheWindow() {
        if (interpreter != null) {
            String closing = run("unview win");
            assertThat(frame == null || !frame.isDisplayable())
                    .as("unview answered %s and the window is still open", closing)
                    .isTrue();
        }
    }

    private String run(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private void forgetWhatWasSeen() {
        takeEventsForAMoment();
        run("clear seen");
    }

    private void takeEventsForAMoment() {
        run("wait [system/ports/event 0.05]");
    }

    private static JFrame findTheFrame() {
        return Arrays.stream(Frame.getFrames())
                .filter(JFrame.class::isInstance)
                .map(JFrame.class::cast)
                .filter(each -> TITLE.equals(each.getTitle()) && each.isDisplayable())
                .findFirst()
                .orElseThrow();
    }

    private int stateOfTheFrame() {
        AtomicReference<Integer> state = new AtomicReference<>();
        onTheToolkit(() -> state.set(frame.getExtendedState()));
        return state.get();
    }

    private Dimension drawableSize() {
        AtomicReference<Dimension> size = new AtomicReference<>();
        onTheToolkit(() -> size.set(frame.getContentPane().getSize()));
        return size.get();
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

    private void takeEventsUntil(BooleanSupplier arrived) {
        long giveUpAt = System.nanoTime() + PATIENCE.toNanos();
        while (!arrived.getAsBoolean()) {
            assertThat(System.nanoTime())
                    .as("still waiting after %s, having heard %s with the frame in state %d",
                            PATIENCE, run("seen"), stateOfTheFrame())
                    .isLessThan(giveUpAt);
            takeEventsForAMoment();
        }
    }

    private void takeEventsUntilTheWindowFillsItsMaximisedSize() {
        takeEventsUntilHeard("maximize");
        takeEventsUntil(() -> isInState(Frame.MAXIMIZED_BOTH) && run("win/size").equals(drawableSizeAsPair()));
        assertThat(seenWithoutRepeatedResizes()).isIn("[resize maximize]", "[resize maximize resize]");
        forgetWhatWasSeen();
        assertThat(run("win/size")).isEqualTo(drawableSizeAsPair());
    }

    private String drawableSizeAsPair() {
        Dimension size = drawableSize();
        return size.width + "x" + size.height;
    }

    private void takeEventsUntilTheWindowIsBackToItsOwnSize() {
        takeEventsUntil(() -> run("win/size").equals("320x200"));
        forgetWhatWasSeen();
        assertThat(run("win/size")).isEqualTo("320x200");
    }

    private void takeEventsUntilHeard(String type) {
        takeEventsUntil(() -> run("not none? find seen '" + type).equals("#(true)"));
    }

    private void takeEventsUntilSeenEndsWith(String last) {
        takeEventsUntil(() -> run("seen").endsWith(last + "]"));
    }

    private boolean isInState(int wanted) {
        return stateOfTheFrame() == wanted;
    }

    private String seenWithoutRepeatedResizes() {
        return run("""
                kept: copy []
                foreach type seen [if any [type <> 'resize 'resize <> last kept] [append kept type]]
                kept""");
    }

    @Test
    @DisplayName("a script minimises, restores, maximises and activates its window, and hears each change")
    void theScriptArrangesItsWindow() {
        assertThat(run("same? win gui-metric/set 'minimize win")).isEqualTo("#(true)");
        takeEventsUntil(() -> isInState(Frame.ICONIFIED));
        takeEventsUntilSeenEndsWith("minimize");
        assertThat(run("seen")).isEqualTo("[minimize]");

        run("clear seen gui-metric/set 'restore win");
        takeEventsUntil(() -> isInState(Frame.NORMAL));
        takeEventsUntilSeenEndsWith("restore");
        assertThat(seenWithoutRepeatedResizes()).isEqualTo("[resize restore]");

        run("clear seen gui-metric/set 'maximize win");
        takeEventsUntilTheWindowFillsItsMaximisedSize();

        run("clear seen gui-metric/set 'restore win");
        takeEventsUntil(() -> isInState(Frame.NORMAL));
        takeEventsUntilHeard("restore");
        takeEventsUntilTheWindowIsBackToItsOwnSize();

        assertThat(run("same? win gui-metric/set 'activate win")).isEqualTo("#(true)");
        forgetWhatWasSeen();
        assertThat(stateOfTheFrame()).isEqualTo(Frame.NORMAL);
    }

    @Test
    @DisplayName("an operator maximising, restoring and minimising the window is heard the same way")
    void theOperatorArrangesTheWindow() {
        onTheToolkit(() -> frame.setExtendedState(Frame.MAXIMIZED_BOTH));
        takeEventsUntilTheWindowFillsItsMaximisedSize();

        run("clear seen");
        onTheToolkit(() -> frame.setExtendedState(Frame.NORMAL));
        takeEventsUntilHeard("restore");
        assertThat(seenWithoutRepeatedResizes()).startsWith("[resize restore");
        takeEventsUntilTheWindowIsBackToItsOwnSize();

        run("clear seen");
        onTheToolkit(() -> frame.setExtendedState(Frame.ICONIFIED));
        takeEventsUntilSeenEndsWith("minimize");
        assertThat(run("seen")).isEqualTo("[minimize]");

        run("clear seen gui-metric/set 'restore win");
        takeEventsUntil(() -> isInState(Frame.NORMAL));
        takeEventsUntilSeenEndsWith("restore");
        assertThat(seenWithoutRepeatedResizes()).isEqualTo("[resize restore]");
    }

    @Test
    @DisplayName("a script maximises a minimised window straight from the dock, and hears resize then maximize")
    void theScriptMaximisesAMinimisedWindow() {
        run("gui-metric/set 'minimize win");
        takeEventsUntilSeenEndsWith("minimize");

        run("clear seen gui-metric/set 'maximize win");
        takeEventsUntilTheWindowFillsItsMaximisedSize();
    }
}
