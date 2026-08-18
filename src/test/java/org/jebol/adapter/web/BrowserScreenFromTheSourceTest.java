package org.jebol.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.ScreenEventKind;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintInstruction;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * A browser as a third screen, not a second dialect.
 *
 * <p>What was here before read a layout block straight into markup and never
 * made a gob at all. It worked, and it was a second implementation of the
 * dialect rather than a third renderer of one, so the two would drift and only
 * one of them was what REBOL's own library talks to.
 *
 * <p>So a browser implements the same port a desktop window does. VIEW,
 * UNVIEW, DO-EVENTS and the handler list are the same borrowed REBOL either
 * way, and the only difference is which adapter executes the paint list.
 *
 * <p>Nothing here mentions HTTP, and that is the design rather than an
 * omission. The port hands over a paint list and takes events back; whether
 * that travels as server-sent events, over a socket, or through a host's own
 * web framework is the host's business, and JEBOL exists to run inside a host
 * that already has one.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
class BrowserScreenFromTheSourceTest {

    private static final String TRUE = "#(true)";

    /** A browser that records what it was told to paint. */
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

    /**
     * A browser that has attached and said how big it is.
     *
     * <p>Saying so is part of attaching, not an extra step. A page that has
     * not reported its viewport is a screen of no size, and everything on it
     * clips away to nothing.
     */
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

    /** A page that has attached and never said how big it is. */
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
            // The whole claim, in one assertion, and it is a comparison
            // between the two renderers rather than either against itself.
            // The browser paints the page; a desktop paints the window. For
            // the same window they are handed the same instructions, because
            // neither of them worked any of it out.
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

            screen.theBrowserReports(ScreenEventKind.DOWN, screen.whatIsShowing().getFirst());
            screen.theBrowserReports(ScreenEventKind.CLOSE, screen.whatIsShowing().getFirst());
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

            screen.theBrowserReports(
                    ScreenEventKind.CLOSE, screen.whatIsShowing().getFirst());
            interpreter.run("do-events");

            assertThat(interpreter.display(interpreter.run(
                    "tail? system/view/screen-gob")))
                    .as("a person shutting a browser tab ends the wait exactly as "
                            + "a person shutting a window does")
                    .isEqualTo(TRUE);
        }
    }
}
