package org.jebol.adapter.web;

import org.jebol.application.Bounds;
import org.jebol.application.Conclusion;
import org.jebol.application.Interpreter;
import org.jebol.application.ScriptOutcome;
import org.jebol.application.TextMeasures;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
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

        private final List<List<DrawingCommand>> painted = new ArrayList<>();
        private boolean looking = true;

        @Override
        public boolean isConnected() {
            return looking;
        }

        @Override
        public void paint(List<DrawingCommand> painting) {
            painted.add(painting);
        }

        void wentAway() {
            looking = false;
        }

        static SomebodyLooking makeOneWhoNeverArrived() {
            SomebodyLooking nobody = new SomebodyLooking();
            nobody.looking = false;
            return nobody;
        }

        List<DrawingCommand> readTheLastPainted() {
            return painted.getLast();
        }

        int timesPainted() {
            return painted.size();
        }
    }

    private static Interpreter makeAnInterpreterWithABrowser(BrowserScreen screen) {
        return makeAnInterpreterWithABrowser(screen, 800, 600);
    }

    private static Interpreter makeAnInterpreterWithABrowser(
            BrowserScreen screen, int wide, int high) {

        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard()
                        .grant(HostService.WINDOWS)
                        .withTimeLimit(Duration.ofSeconds(10)));
        interpreter.useScreen(screen);
        screen.measure(wide, high);
        return interpreter;
    }

    private static Interpreter makeAnInterpreterWithASilentBrowser(BrowserScreen screen) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().grant(HostService.WINDOWS));
        interpreter.useScreen(screen);
        return interpreter;
    }

    private static String sessionOn(BrowserScreen screen, String script) {
        Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
        interpreter.defineNewWordsIn(script);
        return interpreter.display(interpreter.run(script));
    }

    @Nested
    @DisplayName("with somebody looking at the page")
    class TheAttachedBrowser {

        @Test
        @DisplayName("showing a gob sends a paint list, not markup")
        @Timeout(20)
        void sendsAPaintListOnShowing() {
            SomebodyLooking viewer = new SomebodyLooking();

            sessionOn(BrowserScreen.showTo(viewer), """
                    view/no-wait make gob! [size: 320x200 color: 30.34.44]""");

            assertThat(viewer.timesPainted()).isPositive();
            assertThat(viewer.readTheLastPainted())
                    .isNotEmpty()
                    .allMatch(each -> each instanceof DrawingCommand);
        }

        @Test
        @DisplayName("and it is instruction for instruction what a desktop window gets")
        @Timeout(20)
        void thelistIsTheSameOneADesktopGets() {
            SomebodyLooking viewer = new SomebodyLooking();
            Interpreter interpreter = makeAnInterpreterWithABrowser(BrowserScreen.showTo(viewer));
            String describing = """
                    parent: make gob! [size: 200x100 color: 10.20.30]
                    append parent make gob! [offset: 5x5 size: 40x40 color: 200.0.0]
                    window: view/no-wait parent
                    window""";
            interpreter.defineNewWordsIn(describing);
            GobValue window = (GobValue) interpreter.run(describing).value();

            assertThat(viewer.readTheLastPainted())
                    .isEqualTo(PaintList.ofAWindow(window, null)
                            .calls(TextMeasures.ofTheCarriedFont()));
        }

        @Test
        @DisplayName("two windows shown between two looks are one picture, holding both")
        @Timeout(20)
        void paintsTwoWindowsAsOnePicture() {
            SomebodyLooking viewer = new SomebodyLooking();
            Interpreter interpreter = makeAnInterpreterWithABrowser(BrowserScreen.showTo(viewer));
            String opening = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    view/no-wait make gob! [size: 100x100 color: 2.2.2]""";
            interpreter.defineNewWordsIn(opening);
            interpreter.run(opening);
            GobValue root = (GobValue) interpreter.run("system/view/screen-gob").value();

            assertThat(viewer.timesPainted())
                    .as("a page is told the tree as it stands, not once per show")
                    .isEqualTo(1);
            assertThat(viewer.readTheLastPainted())
                    .isEqualTo(PaintList.ofScreen(root, 800, 600, null)
                            .calls(TextMeasures.ofTheCarriedFont()));
        }

        @Test
        @DisplayName("and closing one between two looks is one picture, with what went gone")
        @Timeout(20)
        void paintsClosingAsOnePicture() {
            SomebodyLooking viewer = new SomebodyLooking();
            Interpreter interpreter = makeAnInterpreterWithABrowser(BrowserScreen.showTo(viewer));
            String opening = """
                    keep: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    closing: view/no-wait make gob! [size: 50x50 color: 2.2.2]
                    unview closing""";
            interpreter.defineNewWordsIn(opening);
            interpreter.run(opening);
            GobValue root = (GobValue) interpreter.run("system/view/screen-gob").value();

            assertThat(viewer.timesPainted()).isEqualTo(1);
            assertThat(viewer.readTheLastPainted())
                    .as("the picture the page last gets is the tree as it now stands")
                    .isEqualTo(PaintList.ofScreen(root, 800, 600, null)
                            .calls(TextMeasures.ofTheCarriedFont()));
        }

        @Test
        @DisplayName("faces shown while one event is handled are one picture, not three")
        @Timeout(20)
        void paintsThreeShowsInOneEventAsOnePicture() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
            String setUp = """
                    w: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    w/offset: 0x0
                    inner: make gob! [size: 10x10 color: 2.2.2]
                    append w inner
                    times-shown: 0
                    handle-events [
                        name: 'shower
                        priority: 90
                        handler: func [event] [
                            times-shown: times-shown + 1
                            show inner
                            show inner
                            show w
                            event
                        ]
                    ]""";
            interpreter.defineNewWordsIn(setUp);
            interpreter.run(setUp);
            int paintedBefore = viewer.timesPainted();

            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(5, 5));
            interpreter.run("wait [system/ports/event 0]");

            assertThat(interpreter.display(interpreter.run("times-shown"))).isEqualTo("1");
            assertThat(viewer.timesPainted() - paintedBefore)
                    .as("three shows between two looks are offered as one picture")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("what the browser says about itself")
    class TheMeasurements {

        private String describeAPageOf1280By800(String asking) {
            Interpreter interpreter = makeAnInterpreterWithABrowser(
                    BrowserScreen.showTo(new SomebodyLooking()), 1280, 800);
            return interpreter.display(interpreter.run(asking));
        }

        @Test
        @DisplayName("its viewport is the screen a script measures")
        void takeTheViewportAsTheScreen() {
            assertThat(describeAPageOf1280By800("gui-metric 'screen-size"))
                    .isEqualTo("1280x800");
        }

        @Test
        @DisplayName("and there is one of it")
        void countOneDisplay() {
            assertThat(describeAPageOf1280By800("gui-metric 'screens")).isEqualTo("1");
        }

        @Test
        @DisplayName("a page has no title bar and no window frame")
        void apageHasNoFurniture() {
            assertThat(describeAPageOf1280By800("gui-metric 'title-size"))
                    .isEqualTo("0x0");
            assertThat(describeAPageOf1280By800("gui-metric 'border-size"))
                    .isEqualTo("0x0");
        }

        @Test
        @DisplayName("and its usable area is the whole of it, which a window's never is")
        void useTheWholePage() {
            assertThat(describeAPageOf1280By800("gui-metric 'work-size"))
                    .isEqualTo("1280x800");
            assertThat(describeAPageOf1280By800("gui-metric 'work-origin"))
                    .isEqualTo("0x0");
        }

        @Test
        @DisplayName("the screen gob is sized to the page, so a centred window centres")
        void thescreenGobFollowsTheViewport() {
            Interpreter interpreter = makeAnInterpreterWithABrowser(
                    BrowserScreen.showTo(new SomebodyLooking()), 1280, 800);

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size"))).isEqualTo("1280x800");
        }

        @Test
        @DisplayName("and it follows the page being resized, not only the first measure")
        void thescreenGobFollowsAResize() {
            BrowserScreen screen = BrowserScreen.showTo(new SomebodyLooking());
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen, 1280, 800);

            screen.measure(640, 480);

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size")))
                    .as("a root sized once is wrong from the first drag onwards")
                    .isEqualTo("640x480");
        }

        @Test
        @DisplayName("a browser that never said how big it is gets zeros")
        void abrowserThatNeverSaidGetsZeros() {
            Interpreter interpreter = makeAnInterpreterWithASilentBrowser(
                    BrowserScreen.showTo(new SomebodyLooking()));

            assertThat(interpreter.display(interpreter.run("gui-metric 'screen-size")))
                    .isEqualTo("0x0");
        }
    }

    @Nested
    @DisplayName("a measurement the page posted is taken by the interpreter, not applied where it arrives")
    class TheTakingOfAScreenMeasurement {

        @Test
        @DisplayName("nothing changes until the interpreter looks, and a look takes what was posted")
        void changesNothingUntilTheInterpreterLooks() {
            BrowserScreen screen = BrowserScreen.showTo(new SomebodyLooking());
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen, 640, 480);

            assertThat(screen.measure(ScreenMetric.SCREEN_SIZE, 0))
                    .as("the measurement is queued, and the interpreter has not looked yet")
                    .isEqualTo(PairValue.of(0, 0));

            assertThat(interpreter.run("1").succeeded()).isTrue();

            assertThat(screen.measure(ScreenMetric.SCREEN_SIZE, 0))
                    .isEqualTo(PairValue.of(640, 480));
        }

        @Test
        @DisplayName("the last measurement posted before a take is the one taken")
        void keepsTheLastMeasurementPosted() {
            BrowserScreen screen = BrowserScreen.showTo(new SomebodyLooking());
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen, 640, 480);
            screen.measure(100, 50);

            assertThat(interpreter.run("1").succeeded()).isTrue();

            assertThat(screen.measure(ScreenMetric.SCREEN_SIZE, 0))
                    .isEqualTo(PairValue.of(100, 50));
        }

        @Test
        @DisplayName("a posting that cannot be a size is dropped, and the taken viewport stands")
        void dropsAPostingThatCannotBeASize() {
            BrowserScreen screen = BrowserScreen.showTo(new SomebodyLooking());
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen, 640, 480);
            interpreter.run("1");

            screen.measure(-1, 480);

            assertThat(interpreter.run("1").succeeded()).isTrue();
            assertThat(screen.measure(ScreenMetric.SCREEN_SIZE, 0))
                    .isEqualTo(PairValue.of(640, 480));
        }

        @Test
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        @DisplayName("a resize arriving while the script waits is taken at the next take, inside the wait")
        void takesAResizeWhileWaiting() throws Exception {
            BrowserScreen screen = BrowserScreen.showTo(new SomebodyLooking());
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().grant(HostService.WINDOWS));
            interpreter.useScreen(screen);
            screen.measure(800, 600);
            interpreter.run("view/no-wait make gob! [size: 100x100 color: 1.1.1]");

            AScriptOnItsOwnThread waiting = new AScriptOnItsOwnThread(interpreter, "do-events");
            screen.measure(640, 480);

            waitUntilTheViewportIs(screen, 640, 480);
            interpreter.cancel();
            assertThat(waiting.outcomeWithin(Duration.ofSeconds(5)).conclusion())
                    .isEqualTo(Conclusion.CANCELLED);
        }

        private void waitUntilTheViewportIs(BrowserScreen screen, int wide, int high) {
            long givenUpAt = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (!screen.measure(ScreenMetric.SCREEN_SIZE, 0).equals(PairValue.of(wide, high))) {
                if (System.nanoTime() > givenUpAt) {
                    throw new AssertionError("the viewport never became " + wide + "x" + high);
                }
                Thread.onSpinWait();
            }
        }
    }

    @Nested
    @DisplayName("with nobody looking")
    class TheEmptyPage {

        @Test
        @DisplayName("the screen says it has no display")
        void ithasNoDisplay() {
            assertThat(BrowserScreen.showTo(SomebodyLooking.makeOneWhoNeverArrived())
                    .hasDisplay()).isFalse();
        }

        @Test
        @DisplayName("showing refuses, and says the screen is not present")
        @Timeout(20)
        void refusesToShow() {
            assertThat(sessionOn(
                    BrowserScreen.showTo(SomebodyLooking.makeOneWhoNeverArrived()), """
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
                    BrowserScreen.showTo(SomebodyLooking.makeOneWhoNeverArrived()),
                    "gob? system/view/screen-gob")).isEqualTo(TRUE);
        }

        @Test
        @DisplayName("and a browser that leaves takes the screen with it")
        @Timeout(20)
        void abrowserThatLeavesTakesTheScreen() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
            String script = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]""";
            interpreter.defineNewWordsIn(script);
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
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
            String setUp = """
                    w: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/type  event]
                    ]""";
            interpreter.defineNewWordsIn(setUp);
            interpreter.run(setUp);

            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(5, 5));
            screen.report(ScreenEventType.CLOSE, new ScreenEventDetail.NothingMore());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("mold seen")))
                    .as("one handler block works on a desktop and in a browser "
                            + "without knowing which it is under")
                    .isEqualTo("\"[down close]\"");
        }

        @Test
        @DisplayName("and a close from the browser ends a waiting script")
        @Timeout(20)
        void endAWaitingScriptOnClose() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
            String opening = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]""";
            interpreter.defineNewWordsIn(opening);
            interpreter.run(opening);

            screen.report(ScreenEventType.CLOSE, new ScreenEventDetail.NothingMore());
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
        void tellAHandlerWhereTheClickWas() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
            String setUp = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    system/view/screen-gob/1/offset: 30x40
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/offset  event]
                    ]""";
            interpreter.defineNewWordsIn(setUp);
            interpreter.run(setUp);

            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(35, 47));
            screen.report(ScreenEventType.CLOSE, new ScreenEventDetail.NothingMore());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("first seen"))).isEqualTo("5x7");
        }

        @Test
        @DisplayName("with two windows open, a click reaches only the window it landed on")
        @Timeout(20)
        void reachOnlyTheWindowAClickLandedOn() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
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
            interpreter.defineNewWordsIn(setUp);
            interpreter.run(setUp);

            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(250, 50));
            screen.report(ScreenEventType.UP, new ScreenEventDetail.At(250, 50));
            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(50, 50));
            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(150, 50));
            interpreter.run("wait [system/ports/event 0]");

            assertThat(interpreter.display(interpreter.run("seen")))
                    .isEqualTo("[down right up right down left]");
        }

        @Test
        @DisplayName("a click is judged against where the windows stand when the script takes it, not when the page posted it")
        @Timeout(20)
        void judgeAClickWhenTaken() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
            String setUp = """
                    w: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    w/offset: 0x0
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/offset  event]
                    ]""";
            interpreter.defineNewWordsIn(setUp);
            interpreter.run(setUp);

            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(450, 450));
            interpreter.run("w/offset: 400x400");
            interpreter.run("wait [system/ports/event 0]");

            assertThat(interpreter.display(interpreter.run("seen"))).isEqualTo("[50x50]");
        }

        @Test
        @DisplayName("a gob put in the screen's pane but never shown has no window, so a click on it reaches nothing")
        @Timeout(20)
        void giveNoClickToAGobNeverShown() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
            String setUp = """
                    append system/view/screen-gob make gob! [offset: 0x0 size: 100x100]
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/type  event]
                    ]""";
            interpreter.defineNewWordsIn(setUp);
            interpreter.run(setUp);

            screen.report(ScreenEventType.DOWN, new ScreenEventDetail.At(50, 50));
            interpreter.run("wait [system/ports/event 0]");

            assertThat(interpreter.display(interpreter.run("seen"))).isEqualTo("[]");
        }

        @Test
        @DisplayName("closing the page closes every window on it, each window hearing its own close")
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void closeEveryWindowWhenThePageCloses() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
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
            interpreter.defineNewWordsIn(setUp);
            interpreter.run(setUp);

            screen.report(ScreenEventType.CLOSE, new ScreenEventDetail.NothingMore());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run("seen"))).isEqualTo("[close first close second]");
            assertThat(interpreter.display(interpreter.run("tail? system/view/screen-gob"))).isEqualTo(TRUE);
        }

        @Test
        @Timeout(20)
        @DisplayName("a resize or an offset posted from the page names no window and reaches nothing")
        void deliverNoPostedResizeOrOffset() {
            SomebodyLooking viewer = new SomebodyLooking();
            BrowserScreen screen = BrowserScreen.showTo(viewer);
            Interpreter interpreter = makeAnInterpreterWithABrowser(screen);
            String setUp = """
                    w: view/no-wait make gob! [size: 100x100 color: 1.1.1]
                    seen: copy []
                    handle-events [
                        name: 'watcher
                        priority: 90
                        handler: func [event] [append seen event/type  event]
                    ]""";
            interpreter.defineNewWordsIn(setUp);
            interpreter.run(setUp);

            screen.report(ScreenEventType.RESIZE, new ScreenEventDetail.NothingMore());
            screen.report(ScreenEventType.OFFSET, new ScreenEventDetail.NothingMore());
            interpreter.run("wait [system/ports/event 0]");

            assertThat(interpreter.display(interpreter.run("mold seen"))).isEqualTo("\"[]\"");
        }
    }

    @Nested
    @DisplayName("a wait on a window nobody closes")
    class AWaitNobodyEnds {

        private final BrowserScreen screen = BrowserScreen.showTo(new SomebodyLooking());

        private Interpreter openAWindowUnder(Bounds bounds) {
            Interpreter interpreter = Interpreter.withBounds(bounds.grant(HostService.WINDOWS));
            interpreter.useScreen(screen);
            screen.measure(800, 600);
            String opening = """
                    view/no-wait make gob! [size: 100x100 color: 1.1.1]""";
            interpreter.defineNewWordsIn(opening);
            interpreter.run(opening);
            return interpreter;
        }

        @ParameterizedTest(name = "{0} is ended by a deadline of 300ms")
        @ValueSource(strings = {"do-events", "wait system/ports/event", "wait [system/ports/event 30]", "wait 30"})
        @DisplayName("is ended by the run's deadline, and the run says it timed out")
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void isEndedByTheDeadline(String waiting) {
            Interpreter interpreter = openAWindowUnder(
                    Bounds.standard().withTimeLimit(Duration.ofMillis(300)));

            ScriptOutcome outcome = interpreter.run(waiting);

            assertThat(outcome.conclusion()).isEqualTo(Conclusion.TIMED_OUT);
            assertThat(outcome.elapsed()).isGreaterThanOrEqualTo(Duration.ofMillis(300))
                    .isLessThan(Duration.ofSeconds(5));
        }

        @Test
        @DisplayName("is ended by the host cancelling the run, and the run says it was cancelled")
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void isEndedByTheHostCancelling() throws Exception {
            Interpreter interpreter = openAWindowUnder(Bounds.standard());
            AScriptOnItsOwnThread waiting = new AScriptOnItsOwnThread(interpreter, "do-events");

            waiting.waitUntilItIsParkedInsideTheWait();
            interpreter.cancel();

            assertThat(waiting.outcomeWithin(Duration.ofSeconds(5)).conclusion()).isEqualTo(Conclusion.CANCELLED);
        }

        @Test
        @DisplayName("is ended when the thread running it is interrupted, rather than spinning")
        @Timeout(value = 20, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void isEndedByAnInterrupt() throws Exception {
            Interpreter interpreter = openAWindowUnder(Bounds.standard());
            AScriptOnItsOwnThread waiting = new AScriptOnItsOwnThread(interpreter, "do-events");

            waiting.waitUntilItIsParkedInsideTheWait();
            waiting.interrupt();

            assertThat(waiting.outcomeWithin(Duration.ofSeconds(5)).conclusion())
                    .isEqualTo(Conclusion.PRODUCED_VALUE);
        }
    }

    private static final class AScriptOnItsOwnThread {

        private final CompletableFuture<ScriptOutcome> outcome = new CompletableFuture<>();
        private final Thread running;

        AScriptOnItsOwnThread(Interpreter interpreter, String source) {
            running = Thread.ofPlatform().start(() -> outcome.complete(interpreter.run(source)));
        }

        void waitUntilItIsParkedInsideTheWait() {
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
