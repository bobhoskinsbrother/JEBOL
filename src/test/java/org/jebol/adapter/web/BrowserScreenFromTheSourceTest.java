package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintInstruction;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserScreenFromTheSourceTest {

    private static final String TRUE = "#(true)";

    private static final class SomebodyLooking implements BrowserScreen.Viewer {

        private final List<PaintList> painted = new ArrayList<>();
        private boolean looking = true;

        @Override
        public boolean isConnected() {
            return looking;
        }

        @Override
        public void paint(PaintList painting) {
            painted.add(painting);
        }

        void wentAway() {
            looking = false;
        }

        static SomebodyLooking whoNeverArrived() {
            SomebodyLooking nobody = new SomebodyLooking();
            nobody.looking = false;
            return nobody;
        }

        PaintList lastPainted() {
            return painted.getLast();
        }

        int timesPainted() {
            return painted.size();
        }
    }

    private static Interpreter withABrowser(BrowserScreen screen) {
        return withABrowser(screen, 800, 600);
    }

    private static Interpreter withABrowser(
            BrowserScreen screen, int wide, int high) {

        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .granting(HostService.WINDOWS)
                        .withWallClockLimit(Duration.ofSeconds(10)));
        interpreter.useScreen(screen);
        screen.theBrowserMeasures(wide, high);
        return interpreter;
    }

    private static Interpreter withASilentBrowser(BrowserScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private static String sessionOn(BrowserScreen screen, String script) {
        Interpreter interpreter = withABrowser(screen);
        interpreter.defineFreshWordsIn(script);
        return interpreter.display(interpreter.run(script));
    }

    @Nested
    @DisplayName("with somebody looking at the page")
    class TheAttachedBrowser {

        @Test
        @DisplayName("showing a gob sends a paint list, not markup")
        @Timeout(20)
        void showingSendsAPaintList() {
            SomebodyLooking viewer = new SomebodyLooking();

            sessionOn(BrowserScreen.seenBy(viewer), """
                    view/no-wait make gob! [size: 320x200 color: 30.34.44]""");

            assertThat(viewer.timesPainted()).isPositive();
            assertThat(viewer.lastPainted().instructions())
                    .isNotEmpty()
                    .allMatch(each -> each instanceof PaintInstruction);
        }

        @Test
        @DisplayName("and it is instruction for instruction what a desktop window gets")
        @Timeout(20)
        void thelistIsTheSameOneADesktopGets() {
            SomebodyLooking viewer = new SomebodyLooking();
            Interpreter interpreter = withABrowser(BrowserScreen.seenBy(viewer));
            String describing = """
                    parent: make gob! [size: 200x100 color: 10.20.30]
                    append parent make gob! [offset: 5x5 size: 40x40 color: 200.0.0]
                    window: view/no-wait parent
                    window""";
            interpreter.defineFreshWordsIn(describing);
            GobValue window = (GobValue) interpreter.run(describing).value();

            assertThat(viewer.lastPainted().instructions())
                    .isEqualTo(PaintList.ofAWindow(window, null).instructions());
        }

        @Test
        @DisplayName("a second window paints again, so the page holds both")
        @Timeout(20)
        void asecondWindowPaintsAgain() {
            SomebodyLooking viewer = new SomebodyLooking();

            sessionOn(BrowserScreen.seenBy(viewer), """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    view/no-wait make gob! [size: 100x100 color: 2.2.2]""");

            assertThat(viewer.timesPainted()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("and closing one paints again too, so what went is gone")
        @Timeout(20)
        void closingPaintsAgain() {
            SomebodyLooking viewer = new SomebodyLooking();

            sessionOn(BrowserScreen.seenBy(viewer), """
                    w: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    painted-once: true
                    unview w""");

            assertThat(viewer.timesPainted())
                    .as("a browser is not told to erase; it is told the new picture")
                    .isGreaterThanOrEqualTo(2);
        }
    }

    @Nested
    @DisplayName("what the browser says about itself")
    class TheMeasurements {

        private String whatAPageOf1280By800Says(String asking) {
            Interpreter interpreter = withABrowser(
                    BrowserScreen.seenBy(new SomebodyLooking()), 1280, 800);
            return interpreter.display(interpreter.run(asking));
        }

        @Test
        @DisplayName("its viewport is the screen a script measures")
        void theViewportIsTheScreen() {
            assertThat(whatAPageOf1280By800Says("gui-metric 'screen-size"))
                    .isEqualTo("1280x800");
        }

        @Test
        @DisplayName("and there is one of it")
        void thereIsOneDisplay() {
            assertThat(whatAPageOf1280By800Says("gui-metric 'screens")).isEqualTo("1");
        }

        @Test
        @DisplayName("a page has no title bar and no window frame")
        void apageHasNoFurniture() {
            assertThat(whatAPageOf1280By800Says("gui-metric 'title-size"))
                    .isEqualTo("0x0");
            assertThat(whatAPageOf1280By800Says("gui-metric 'border-size"))
                    .isEqualTo("0x0");
        }

        @Test
        @DisplayName("and its usable area is the whole of it, which a window's never is")
        void theWholePageIsUsable() {
            assertThat(whatAPageOf1280By800Says("gui-metric 'work-size"))
                    .isEqualTo("1280x800");
            assertThat(whatAPageOf1280By800Says("gui-metric 'work-origin"))
                    .isEqualTo("0x0");
        }

        @Test
        @DisplayName("the screen gob is sized to the page, so a centred window centres")
        void thescreenGobFollowsTheViewport() {
            Interpreter interpreter = withABrowser(
                    BrowserScreen.seenBy(new SomebodyLooking()), 1280, 800);

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size"))).isEqualTo("1280x800");
        }

        @Test
        @DisplayName("and it follows the page being resized, not only the first measure")
        void thescreenGobFollowsAResize() {
            BrowserScreen screen = BrowserScreen.seenBy(new SomebodyLooking());
            Interpreter interpreter = withABrowser(screen, 1280, 800);

            screen.theBrowserMeasures(640, 480);

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size")))
                    .as("a root sized once is wrong from the first drag onwards")
                    .isEqualTo("640x480");
        }

        @Test
        @DisplayName("a browser that never said how big it is gets zeros")
        void abrowserThatNeverSaidGetsZeros() {
            Interpreter interpreter = withASilentBrowser(
                    BrowserScreen.seenBy(new SomebodyLooking()));

            assertThat(interpreter.display(interpreter.run("gui-metric 'screen-size")))
                    .isEqualTo("0x0");
        }
    }

    @Nested
    @DisplayName("with nobody looking")
    class TheEmptyPage {

        @Test
        @DisplayName("the screen says it has no display")
        void ithasNoDisplay() {
            assertThat(BrowserScreen.seenBy(SomebodyLooking.whoNeverArrived())
                    .hasADisplay()).isFalse();
        }

        @Test
        @DisplayName("showing refuses, and says the screen is not present")
        @Timeout(20)
        void showingRefuses() {
            assertThat(sessionOn(
                    BrowserScreen.seenBy(SomebodyLooking.whoNeverArrived()), """
                    e: try [view/no-wait make gob! [size: 100x100]]
                    either error? e [form e/arg1] ["no error at all"]"""))
                    .as("a host serving a page nobody has opened is a machine "
                            + "with no display")
                    .contains("not present");
        }

        @Test
        @DisplayName("but the library still loaded, so there is a root gob")
        @Timeout(20)
        void thelibraryStillLoaded() {
            assertThat(sessionOn(
                    BrowserScreen.seenBy(SomebodyLooking.whoNeverArrived()),
                    "gob? system/view/screen-gob")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a browser that leaves takes the screen with it")
        @Timeout(20)
        void abrowserThatLeavesTakesTheScreen() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.seenBy(viewer);
            Interpreter interpreter = withABrowser(screen);
            String script = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]""";
            interpreter.defineFreshWordsIn(script);
            interpreter.run(script);

            viewer.wentAway();

            assertThat(interpreter.display(interpreter.run("""
                    error? try [show system/view/screen-gob]"""))).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("what the browser sends back")
    class TheEvents {

        @Test
        @DisplayName("an event names a window and joins the same queue every event joins")
        @Timeout(20)
        void aneventJoinsTheSameQueue() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.seenBy(viewer);
            Interpreter interpreter = withABrowser(screen);
            String setUp = """
                    w: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/type  event]
                    ]""";
            interpreter.defineFreshWordsIn(setUp);
            interpreter.run(setUp);

            screen.theBrowserReports(ScreenEventKind.DOWN, new ScreenEventDetail.At(5, 5));
            screen.theBrowserReports(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .as("one handler block works on a desktop and in a browser "
                            + "without knowing which it is under")
                    .isEqualTo("\"[down close]\"");
        }

        @Test
        @DisplayName("and a close from the browser ends a waiting script")
        @Timeout(20)
        void aCloseEndsAWaitingScript() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.seenBy(viewer);
            Interpreter interpreter = withABrowser(screen);
            String opening = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]""";
            interpreter.defineFreshWordsIn(opening);
            interpreter.run(opening);

            screen.theBrowserReports(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run(
                    "tail? system/view/screen-gob")))
                    .as("a person shutting a browser tab ends the wait exactly as "
                            + "a person shutting a window does")
                    .isEqualTo(TRUE);
        }

        @Test
        @DisplayName("a handler reads where a click was, counted from its window's top left as on a desktop")
        @Timeout(20)
        void aHandlerReadsWhereTheClickWas() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.seenBy(viewer);
            Interpreter interpreter = withABrowser(screen);
            String setUp = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    system/view/screen-gob/1/offset: 30x40
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/offset  event]
                    ]""";
            interpreter.defineFreshWordsIn(setUp);
            interpreter.run(setUp);

            screen.theBrowserReports(ScreenEventKind.DOWN, new ScreenEventDetail.At(35, 47));
            screen.theBrowserReports(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("first seen"))).isEqualTo("5x7");
        }

        @Test
        @DisplayName("with two windows open, a click reaches only the window it landed on")
        @Timeout(20)
        void aClickReachesOnlyTheWindowItLandedOn() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.seenBy(viewer);
            Interpreter interpreter = withABrowser(screen);
            String setUp = """
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
                            event
                        ]
                    ]""";
            interpreter.defineFreshWordsIn(setUp);
            interpreter.run(setUp);

            screen.theBrowserReports(ScreenEventKind.DOWN, new ScreenEventDetail.At(250, 50));
            screen.theBrowserReports(ScreenEventKind.UP, new ScreenEventDetail.At(250, 50));
            screen.theBrowserReports(ScreenEventKind.DOWN, new ScreenEventDetail.At(50, 50));
            screen.theBrowserReports(ScreenEventKind.DOWN, new ScreenEventDetail.At(150, 50));
            interpreter.run("wait [system/ports/event 0]");

            assertThat(interpreter.display(interpreter.run("seen")))
                    .isEqualTo("[down right up right down left]");
        }

        @Test
        @DisplayName("a click is judged against where the windows stand when the script takes it, not when the page posted it")
        @Timeout(20)
        void aClickIsJudgedWhenTaken() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.seenBy(viewer);
            Interpreter interpreter = withABrowser(screen);
            String setUp = """
                    w: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    w/offset: 0x0
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/offset  event]
                    ]""";
            interpreter.defineFreshWordsIn(setUp);
            interpreter.run(setUp);

            screen.theBrowserReports(ScreenEventKind.DOWN, new ScreenEventDetail.At(450, 450));
            interpreter.run("w/offset: 400x400");
            interpreter.run("wait [system/ports/event 0]");

            assertThat(interpreter.display(interpreter.run("seen"))).isEqualTo("[50x50]");
        }

        @Test
        @DisplayName("a gob put in the screen's pane but never shown has no window, so a click on it reaches nothing")
        @Timeout(20)
        void aGobNeverShownTakesNoClick() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.seenBy(viewer);
            Interpreter interpreter = withABrowser(screen);
            String setUp = """
                    append system/view/screen-gob make gob! [offset: 0x0 size: 100x100]
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/type  event]
                    ]""";
            interpreter.defineFreshWordsIn(setUp);
            interpreter.run(setUp);

            screen.theBrowserReports(ScreenEventKind.DOWN, new ScreenEventDetail.At(50, 50));
            interpreter.run("wait [system/ports/event 0]");

            assertThat(interpreter.display(interpreter.run("seen"))).isEqualTo("[]");
        }

        @Test
        @DisplayName("closing the page closes every window on it, each window hearing its own close")
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void closingThePageClosesEveryWindow() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.seenBy(viewer);
            Interpreter interpreter = withABrowser(screen);
            String setUp = """
                    first-window: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    second-window: view/no-wait make gob! [size: 100x100 color: 2.2.2]
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [
                            append seen reduce [
                                event/type
                                case [
                                    same? event/window first-window ['first]
                                    same? event/window second-window ['second]
                                    true ['neither]
                                ]
                            ]
                            unview event/window
                            event
                        ]
                    ]""";
            interpreter.defineFreshWordsIn(setUp);
            interpreter.run(setUp);

            screen.theBrowserReports(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("seen"))).isEqualTo("[close first close second]");
            assertThat(interpreter.display(interpreter.run("tail? system/view/screen-gob"))).isEqualTo(TRUE);
        }
    }

    @Nested
    @DisplayName("a wait on a window nobody closes")
    class AWaitNobodyEnds {

        private final BrowserScreen screen = BrowserScreen.seenBy(new SomebodyLooking());

        private Interpreter withAWindowOpenUnder(Bounds bounds) {
            Interpreter interpreter = Interpreter.withBounds(bounds.granting(HostService.WINDOWS));
            interpreter.useScreen(screen);
            screen.theBrowserMeasures(800, 600);
            String opening = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]""";
            interpreter.defineFreshWordsIn(opening);
            interpreter.run(opening);
            return interpreter;
        }

        @ParameterizedTest(name = "{0} is ended by a deadline of 300ms")
        @ValueSource(strings = {"do-events", "wait system/ports/event", "wait [system/ports/event 30]", "wait 30"})
        @DisplayName("is ended by the run's deadline, and the run says it timed out")
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void isEndedByTheDeadline(String waiting) {
            Interpreter interpreter = withAWindowOpenUnder(
                    Bounds.standard().withWallClockLimit(Duration.ofMillis(300)));

            ScriptOutcome outcome = interpreter.run(waiting);

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.TIMED_OUT);
            assertThat(outcome.elapsed()).isGreaterThanOrEqualTo(Duration.ofMillis(300))
                    .isLessThan(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("is ended by the host cancelling the run, and the run says it was cancelled")
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void isEndedByTheHostCancelling() throws Exception {
            Interpreter interpreter = withAWindowOpenUnder(Bounds.standard());
            AScriptOnItsOwnThread waiting = new AScriptOnItsOwnThread(interpreter, "do-events");

            waiting.untilItIsParkedInsideTheWait();
            interpreter.cancel();

            assertThat(waiting.outcomeWithin(Duration.ofSeconds(5)).conclusion()).isEqualTo(Conclusion.CANCELLED);
        }

        @Test
        @DisplayName("is ended when the thread running it is interrupted, rather than spinning")
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void isEndedByAnInterrupt() throws Exception {
            Interpreter interpreter = withAWindowOpenUnder(Bounds.standard());
            AScriptOnItsOwnThread waiting = new AScriptOnItsOwnThread(interpreter, "do-events");

            waiting.untilItIsParkedInsideTheWait();
            waiting.interrupt();

            assertThat(waiting.outcomeWithin(Duration.ofSeconds(5)).conclusion())
                    .isEqualTo(Conclusion.PRODUCED_A_VALUE);
        }
    }

    private static final class AScriptOnItsOwnThread {

        private final CompletableFuture<ScriptOutcome> outcome = new CompletableFuture<>();
        private final Thread running;

        AScriptOnItsOwnThread(Interpreter interpreter, String source) {
            running = Thread.ofPlatform().start(() -> outcome.complete(interpreter.run(source)));
        }

        void untilItIsParkedInsideTheWait() {
            while (running.getState() != Thread.State.TIMED_WAITING) {
                assertThat(outcome).as("the script ended before it was ever inside the wait").isNotDone();
                Thread.onSpinWait();
            }
        }

        void interrupt() {
            running.interrupt();
        }

        ScriptOutcome outcomeWithin(Duration allowed) throws Exception {
            return outcome.get(allowed.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
