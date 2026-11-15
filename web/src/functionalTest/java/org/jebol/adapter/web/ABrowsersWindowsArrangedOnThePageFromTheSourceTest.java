package org.jebol.adapter.web;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.render.Colour;
import org.jebol.domain.render.DrawingCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class ABrowsersWindowsArrangedOnThePageFromTheSourceTest {

    private static final Colour LEFTS = new Colour(1, 1, 1);
    private static final Colour RIGHTS = new Colour(2, 2, 2);

    private static final String TWO_WINDOWS = """
            left: view/no-wait make gob! [size: 100x100 color: 1.1.1]
            left/offset: 0x0
            right: view/no-wait make gob! [size: 100x100 color: 2.2.2]
            right/offset: 200x0
            seen: copy []
            handle-events [
                name: 'watcher
                priority: 90
                handler: func [event] [
                    append seen reduce [
                        event/type
                        case [same? event/window right ['right] same? event/window left ['left] true ['neither]]
                    ]
                    if event/type = 'resize [append seen event/offset]
                    event
                ]
            ]""";

    private static final class SomebodyLooking implements BrowserScreen.Viewer {

        private final List<List<DrawingCommand>> painted = new ArrayList<>();

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void paint(List<DrawingCommand> painting) {
            painted.add(painting);
        }

        List<Colour> coloursFilledLast() {
            return painted.getLast().stream()
                    .filter(DrawingCommand.Fill.class::isInstance)
                    .map(each -> ((DrawingCommand.Fill) each).colour())
                    .toList();
        }

        Optional<DrawingCommand.Fill> lastFillIn(Colour colour) {
            return painted.getLast().stream()
                    .filter(DrawingCommand.Fill.class::isInstance)
                    .map(DrawingCommand.Fill.class::cast)
                    .filter(each -> each.colour().equals(colour))
                    .reduce((first, second) -> second);
        }
    }

    private final SomebodyLooking viewer = new SomebodyLooking();
    private final BrowserScreen screen = BrowserScreen.showTo(viewer, new ClasspathFiles());
    private final Interpreter interpreter = Interpreter.createOn(
            AnInterpreter.standardPorts().withScreen(screen),
            OutputPort.discard(),
            Bounds.standard().grant(HostService.WINDOWS).withTimeLimit(Duration.ofSeconds(20)));

    private ABrowsersWindowsArrangedOnThePageFromTheSourceTest() {
        screen.measure(800, 600);
        run(TWO_WINDOWS);
    }

    private String run(String source) {
        interpreter.defineNewWordsIn(source);
        return interpreter.display(interpreter.run(source));
    }

    private String runAndTakeEvents(String source) {
        run(source);
        run("wait [system/ports/event 0]");
        return run("seen");
    }

    @Nested
    @DisplayName("maximize")
    class Maximize {

        @Test
        @DisplayName("puts the window at 0x0 the viewport's size, and says resize then maximize")
        void fillsTheViewport() {
            assertThat(runAndTakeEvents("gui-metric/set 'maximize right"))
                    .isEqualTo("[resize right 800x600 maximize right]");
            assertThat(run("reduce [right/offset right/size]")).isEqualTo("[0x0 800x600]");
        }

        @Test
        @DisplayName("and the page shows its content from the page's corner, clipped to the viewport")
        void isPaintedFromThePagesCorner() {
            runAndTakeEvents("gui-metric/set 'maximize right");

            DrawingCommand.Fill filled = viewer.lastFillIn(RIGHTS).orElseThrow();
            assertThat(List.of(filled.where().across(), filled.where().down(),
                    filled.where().clip().wide(), filled.where().clip().high()))
                    .containsExactly(0, 0, 100, 100);
        }

        @Test
        @DisplayName("and a window viewed as it is fills the page")
        void aWindowAsItIsFillsThePage() {
            run("""
                    whole: view/no-wait/as-is make gob! [size: 100x100 offset: 300x300 color: 3.3.3]
                    gui-metric/set 'maximize whole""");
            run("wait [system/ports/event 0]");

            assertThat(run("reduce [whole/offset whole/size]")).isEqualTo("[0x0 800x600]");
        }

        @Test
        @DisplayName("and keeps it fitted when the viewport changes, saying resize")
        void followsTheViewport() {
            runAndTakeEvents("gui-metric/set 'maximize right");
            screen.measure(640, 480);

            assertThat(runAndTakeEvents(""))
                    .isEqualTo("[resize right 800x600 maximize right resize right 640x480]");
            assertThat(run("reduce [right/offset right/size]")).isEqualTo("[0x0 640x480]");
        }
    }

    @Nested
    @DisplayName("minimize")
    class Minimize {

        @Test
        @DisplayName("says minimize, and nothing else")
        void saysMinimize() {
            assertThat(runAndTakeEvents("gui-metric/set 'minimize right"))
                    .isEqualTo("[minimize right]");
        }

        @Test
        @DisplayName("leaves the window open, at its own size and place")
        void leavesItOpen() {
            runAndTakeEvents("gui-metric/set 'minimize right");

            assertThat(run("reduce [right/offset right/size same? right last system/view/screen-gob/pane]"))
                    .isEqualTo("[200x0 100x100 #(true)]");
        }

        @Test
        @DisplayName("stops it painting")
        void stopsItPainting() {
            runAndTakeEvents("gui-metric/set 'minimize right");

            assertThat(viewer.coloursFilledLast()).contains(LEFTS).doesNotContain(RIGHTS);
        }

        @Test
        @DisplayName("and stops it hearing a press over it")
        void stopsItHearingAPress() {
            runAndTakeEvents("gui-metric/set 'minimize right");
            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(250, 50));

            assertThat(runAndTakeEvents("")).isEqualTo("[minimize right]");
        }

        @Test
        @DisplayName("or a key, which goes to the window still showing")
        void stopsItHearingAKey() {
            runAndTakeEvents("gui-metric/set 'minimize right");
            screen.report(ScreenEventType.KEY, new ScreenEventDetail.Typed('a'));

            assertThat(runAndTakeEvents("")).isEqualTo("[minimize right key left]");
        }
    }

    @Nested
    @DisplayName("restore")
    class Restore {

        @Test
        @DisplayName("after maximize puts back the size and offset, saying resize then restore")
        void undoesMaximize() {
            assertThat(runAndTakeEvents("""
                    gui-metric/set 'maximize right
                    gui-metric/set 'restore right"""))
                    .isEqualTo("[resize right 800x600 maximize right resize right 100x100 restore right]");
            assertThat(run("reduce [right/offset right/size]")).isEqualTo("[200x0 100x100]");
        }

        @Test
        @DisplayName("and the page shows it where it was")
        void isPaintedWhereItWas() {
            runAndTakeEvents("""
                    gui-metric/set 'maximize right
                    gui-metric/set 'restore right""");

            DrawingCommand.Fill filled = viewer.lastFillIn(RIGHTS).orElseThrow();
            assertThat(List.of(filled.where().across(), filled.where().down(),
                    filled.where().wide(), filled.where().high()))
                    .containsExactly(200, 0, 100, 100);
        }

        @Test
        @DisplayName("after minimize shows it again, saying resize then restore")
        void undoesMinimize() {
            assertThat(runAndTakeEvents("""
                    gui-metric/set 'minimize right
                    gui-metric/set 'restore right"""))
                    .isEqualTo("[minimize right resize right 100x100 restore right]");
            assertThat(viewer.coloursFilledLast()).contains(LEFTS, RIGHTS);
        }

        @Test
        @DisplayName("and it hears a press over it again")
        void hearsAgainAfterMinimize() {
            runAndTakeEvents("""
                    gui-metric/set 'minimize right
                    gui-metric/set 'restore right""");
            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(250, 50));

            assertThat(runAndTakeEvents(""))
                    .isEqualTo("[minimize right resize right 100x100 restore right down right]");
        }

        @Test
        @DisplayName("of a window that is neither changes nothing and says nothing")
        void leavesANormalWindowAlone() {
            assertThat(runAndTakeEvents("gui-metric/set 'restore right")).isEqualTo("[]");
            assertThat(run("reduce [right/offset right/size]")).isEqualTo("[200x0 100x100]");
        }

        @Test
        @DisplayName("of a maximised window that was then minimised puts back the size it had before either")
        void restoresAMinimisedMaximisedWindowToItsOwnSize() {
            assertThat(runAndTakeEvents("""
                    gui-metric/set 'maximize right
                    gui-metric/set 'minimize right
                    gui-metric/set 'restore right"""))
                    .isEqualTo("[resize right 800x600 maximize right minimize right resize right 100x100 restore right]");
            assertThat(run("reduce [right/offset right/size]")).isEqualTo("[200x0 100x100]");
            assertThat(viewer.coloursFilledLast()).contains(RIGHTS);
        }
    }

    @Nested
    @DisplayName("activate")
    class Activate {

        private void overlap() {
            run("right/offset: 50x0");
        }

        @Test
        @DisplayName("lifts a lower window to the top, so a press where both are reaches it")
        void liftsTheWindow() {
            overlap();
            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(75, 50));
            screen.report(ScreenEventType.UP, new ScreenEventDetail.At(75, 50));
            runAndTakeEvents("");
            run("gui-metric/set 'activate left");
            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(75, 50));

            assertThat(runAndTakeEvents("")).isEqualTo("[down right up right down left]");
        }

        @Test
        @DisplayName("and paints it last, on top")
        void paintsItOnTop() {
            overlap();
            runAndTakeEvents("show system/view/screen-gob gui-metric/set 'activate left");

            List<Colour> filled = viewer.coloursFilledLast();
            assertThat(filled.lastIndexOf(LEFTS)).isGreaterThan(filled.lastIndexOf(RIGHTS));
        }

        @Test
        @DisplayName("and gives it the keyboard")
        void givesItTheKeyboard() {
            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(250, 50));
            screen.report(ScreenEventType.UP, new ScreenEventDetail.At(250, 50));
            runAndTakeEvents("");
            run("gui-metric/set 'activate left");
            screen.report(ScreenEventType.KEY, new ScreenEventDetail.Typed('a'));

            assertThat(runAndTakeEvents("")).isEqualTo("[down right up right key left]");
        }

        @Test
        @DisplayName("saying nothing, as in both hosts")
        void saysNothing() {
            assertThat(runAndTakeEvents("gui-metric/set 'activate left")).isEqualTo("[]");
        }

        @Test
        @DisplayName("and leaves the screen gob's pane in its own order")
        void leavesThePaneAlone() {
            run("gui-metric/set 'activate left");

            assertThat(run("same? left first system/view/screen-gob/pane")).isEqualTo("#(true)");
        }
    }
}
