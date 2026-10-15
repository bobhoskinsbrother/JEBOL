package org.jebol.domain.host;

import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsOnOneSurfaceTest {

    private WindowsOnOneSurface surface;
    private final List<Reported> reported = new ArrayList<>();

    private record Reported(ScreenEventKind kind, ScreenEventDetail detail) {
    }
    private List<GobValue> showing;
    private GobValue lower;
    private GobValue upper;

    @BeforeEach
    void twoOverlappingWindows() {
        surface = new WindowsOnOneSurface();
        lower = aWindowAt(10, 20, 100, 100);
        upper = aWindowAt(60, 70, 100, 100);
        showing = new ArrayList<>(List.of(lower, upper));
    }

    private GobValue aWindowAt(int across, int down, int wide, int high) {
        GobValue window = GobValue.empty();
        window.storage().offset(PairValue.of(across, down));
        window.storage().size(PairValue.of(wide, high));
        return window;
    }

    private void theSurfaceReports(ScreenEventKind kind, ScreenEventDetail detail) {
        reported.add(new Reported(kind, detail));
    }

    private void thePointer(ScreenEventKind kind, int across, int down) {
        theSurfaceReports(kind, new ScreenEventDetail.At(across, down));
    }

    private List<ScreenEvent> whatIsDelivered() {
        List<ScreenEvent> delivered = new ArrayList<>();
        for (Reported each : reported) {
            delivered.addAll(surface.addressed(each.kind(), each.detail(), List.copyOf(showing)));
        }
        reported.clear();
        return delivered;
    }

    private ScreenEvent anEvent(ScreenEventKind kind, GobValue window, int across, int down) {
        return new ScreenEvent(kind, window, new ScreenEventDetail.At(across, down));
    }

    private ScreenEvent anEvent(ScreenEventKind kind, GobValue window, ScreenEventDetail detail) {
        return new ScreenEvent(kind, window, detail);
    }

    @Nested
    @DisplayName("a press")
    class APress {

        @ParameterizedTest(name = "at {0}x{1} on the surface is {2}x{3} in the lower window")
        @CsvSource({
                "10,  20,  0,  0",
                "11,  21,  1,  1",
                "59,  69,  49, 49",
                "109, 20,  99, 0",
                "10,  119, 0,  99",
        })
        @DisplayName("over one window belongs to it, counted from its top left, its edges included")
        void overOneWindowBelongsToIt(int across, int down, int inAcross, int inDown) {
            thePointer(ScreenEventKind.DOWN, across, down);

            assertThat(whatIsDelivered()).containsExactly(anEvent(ScreenEventKind.DOWN, lower, inAcross, inDown));
        }

        @ParameterizedTest(name = "at {0}x{1} reaches nothing")
        @CsvSource({
                "9,   50",
                "50,  19",
                "9,   19",
                "160, 120",
                "100, 170",
                "0,   0",
                "-1,  -1",
                "110, 20",
                "10,  120",
        })
        @DisplayName("over no window reaches nothing, one pixel past each edge included")
        void overNoWindowReachesNothing(int across, int down) {
            thePointer(ScreenEventKind.DOWN, across, down);

            assertThat(whatIsDelivered()).isEmpty();
        }

        @ParameterizedTest(name = "at {0}x{1}, where both windows are, belongs to the upper as {2}x{3}")
        @CsvSource({
                "60,  70,  0,  0",
                "109, 119, 49, 49",
                "80,  90,  20, 20",
        })
        @DisplayName("where two windows overlap belongs to the one later in the root's pane")
        void whereTwoOverlapBelongsToTheUpper(int across, int down, int inAcross, int inDown) {
            thePointer(ScreenEventKind.DOWN, across, down);

            assertThat(whatIsDelivered()).containsExactly(anEvent(ScreenEventKind.DOWN, upper, inAcross, inDown));
        }

        @Test
        @DisplayName("and when the pane's order is the other way round, the other window is on top")
        void thePanesOrderDecidesWhichIsOnTop() {
            showing = new ArrayList<>(List.of(upper, lower));

            thePointer(ScreenEventKind.DOWN, 80, 90);

            assertThat(whatIsDelivered()).containsExactly(anEvent(ScreenEventKind.DOWN, lower, 70, 70));
        }

        @Test
        @DisplayName("with no window showing reaches nothing")
        void withNoWindowShowingReachesNothing() {
            showing.clear();

            thePointer(ScreenEventKind.DOWN, 50, 50);

            assertThat(whatIsDelivered()).isEmpty();
        }

        @Test
        @DisplayName("a window of no size covers no point at all, not even its own offset")
        void aWindowOfNoSizeCoversNothing() {
            GobValue empty = aWindowAt(300, 300, 0, 0);
            showing.add(empty);

            thePointer(ScreenEventKind.DOWN, 300, 300);

            assertThat(whatIsDelivered()).isEmpty();
        }

        @Test
        @DisplayName("a window placed partly off the surface still takes a press on the part that shows")
        void aWindowPartlyOffTheSurface() {
            GobValue offTheEdge = aWindowAt(-50, -50, 100, 100);
            showing = new ArrayList<>(List.of(offTheEdge));

            thePointer(ScreenEventKind.DOWN, 0, 0);

            assertThat(whatIsDelivered()).containsExactly(anEvent(ScreenEventKind.DOWN, offTheEdge, 50, 50));
        }

        @Test
        @DisplayName("is judged against where the windows stand when it is addressed, not when it was reported")
        void isJudgedWhenAddressed() {
            thePointer(ScreenEventKind.DOWN, 300, 300);
            lower.storage().offset(PairValue.of(250, 250));

            assertThat(whatIsDelivered()).containsExactly(anEvent(ScreenEventKind.DOWN, lower, 50, 50));
        }
    }

    @Nested
    @DisplayName("while a press is held")
    class WhileAPressIsHeld {

        @Test
        @DisplayName("a move off every window still belongs to the window the press began in, counted from it")
        void aMoveOffEveryWindowBelongsToThePressedWindow() {
            thePointer(ScreenEventKind.DOWN, 20, 30);
            thePointer(ScreenEventKind.MOVE, 0, 0);

            assertThat(whatIsDelivered()).containsExactly(
                    anEvent(ScreenEventKind.DOWN, lower, 10, 10),
                    anEvent(ScreenEventKind.MOVE, lower, -10, -20));
        }

        @Test
        @DisplayName("a move over another window, and the release there, still belong to the pressed window")
        void aMoveAndReleaseOverAnotherWindowBelongToThePressedWindow() {
            thePointer(ScreenEventKind.DOWN, 20, 30);
            thePointer(ScreenEventKind.MOVE, 150, 150);
            thePointer(ScreenEventKind.UP, 150, 150);

            assertThat(whatIsDelivered()).containsExactly(
                    anEvent(ScreenEventKind.DOWN, lower, 10, 10),
                    anEvent(ScreenEventKind.MOVE, lower, 140, 130),
                    anEvent(ScreenEventKind.UP, lower, 140, 130));
        }

        @Test
        @DisplayName("and once released, the next move belongs to the window under the pointer again")
        void afterTheReleaseTheWindowUnderThePointerHearsAgain() {
            thePointer(ScreenEventKind.DOWN, 20, 30);
            thePointer(ScreenEventKind.UP, 150, 150);
            thePointer(ScreenEventKind.MOVE, 150, 150);

            assertThat(whatIsDelivered()).last().isEqualTo(anEvent(ScreenEventKind.MOVE, upper, 90, 80));
        }

        @Test
        @DisplayName("a press held over a window that then closes holds nothing, so the window under the pointer hears")
        void aPressedWindowThatClosesHoldsNoPress() {
            thePointer(ScreenEventKind.DOWN, 20, 30);
            whatIsDelivered();
            showing.remove(lower);

            thePointer(ScreenEventKind.MOVE, 150, 150);
            thePointer(ScreenEventKind.MOVE, 0, 0);

            assertThat(whatIsDelivered()).containsExactly(anEvent(ScreenEventKind.MOVE, upper, 90, 80));
        }

        @Test
        @DisplayName("a press on nothing holds nothing, so a move over a window afterwards belongs to that window")
        void aPressOnNothingHoldsNothing() {
            thePointer(ScreenEventKind.DOWN, 0, 0);
            thePointer(ScreenEventKind.MOVE, 20, 30);

            assertThat(whatIsDelivered()).containsExactly(anEvent(ScreenEventKind.MOVE, lower, 10, 10));
        }
    }

    @Nested
    @DisplayName("with no press held")
    class WithNoPressHeld {

        @ParameterizedTest(name = "a {0} over the upper window belongs to it")
        @EnumSource(value = ScreenEventKind.class, names = {"MOVE", "UP"})
        @DisplayName("a move or a release belongs to the topmost window under the pointer")
        void belongsToTheWindowUnderThePointer(ScreenEventKind kind) {
            thePointer(kind, 80, 90);

            assertThat(whatIsDelivered()).containsExactly(anEvent(kind, upper, 20, 20));
        }

        @ParameterizedTest(name = "a {0} over no window reaches nothing")
        @EnumSource(value = ScreenEventKind.class, names = {"MOVE", "UP"})
        @DisplayName("and over no window reaches nothing")
        void overNoWindowReachesNothing(ScreenEventKind kind) {
            thePointer(kind, 9, 19);

            assertThat(whatIsDelivered()).isEmpty();
        }
    }

    @Nested
    @DisplayName("a key")
    class AKey {

        private final ScreenEventDetail typedA = new ScreenEventDetail.Typed('a');

        @ParameterizedTest(name = "a {0} before any press belongs to the topmost window")
        @EnumSource(value = ScreenEventKind.class, names = {"KEY", "KEY_UP", "CONTROL", "CONTROL_UP"})
        @DisplayName("before any press belongs to the topmost window, which a desktop gives a new window")
        void beforeAnyPressBelongsToTheTopmost(ScreenEventKind kind) {
            theSurfaceReports(kind, typedA);

            assertThat(whatIsDelivered()).containsExactly(anEvent(kind, upper, typedA));
        }

        @Test
        @DisplayName("after a press in the lower window belongs to the lower window")
        void afterAPressBelongsToThePressedWindow() {
            thePointer(ScreenEventKind.DOWN, 20, 30);
            thePointer(ScreenEventKind.UP, 20, 30);
            theSurfaceReports(ScreenEventKind.KEY, typedA);

            assertThat(whatIsDelivered()).last().isEqualTo(anEvent(ScreenEventKind.KEY, lower, typedA));
        }

        @Test
        @DisplayName("a named key follows the press too")
        void aNamedKeyFollowsThePress() {
            ScreenEventDetail left = new ScreenEventDetail.NamedKey("left");
            thePointer(ScreenEventKind.DOWN, 20, 30);
            theSurfaceReports(ScreenEventKind.CONTROL, left);

            assertThat(whatIsDelivered()).last().isEqualTo(anEvent(ScreenEventKind.CONTROL, lower, left));
        }

        @Test
        @DisplayName("a press on nothing leaves the keyboard where it was")
        void aPressOnNothingLeavesTheKeyboard() {
            thePointer(ScreenEventKind.DOWN, 20, 30);
            thePointer(ScreenEventKind.UP, 20, 30);
            thePointer(ScreenEventKind.DOWN, 0, 0);
            theSurfaceReports(ScreenEventKind.KEY, typedA);

            assertThat(whatIsDelivered()).last().isEqualTo(anEvent(ScreenEventKind.KEY, lower, typedA));
        }

        @Test
        @DisplayName("once the window pressed in has closed belongs to the topmost window again")
        void onceThePressedWindowClosesBelongsToTheTopmost() {
            GobValue third = aWindowAt(400, 400, 50, 50);
            showing.add(third);
            thePointer(ScreenEventKind.DOWN, 20, 30);
            whatIsDelivered();
            showing.remove(lower);

            theSurfaceReports(ScreenEventKind.KEY, typedA);

            assertThat(whatIsDelivered()).containsExactly(anEvent(ScreenEventKind.KEY, third, typedA));
        }

        @Test
        @DisplayName("with no window showing reaches nothing")
        void withNoWindowShowingReachesNothing() {
            showing.clear();

            theSurfaceReports(ScreenEventKind.KEY, typedA);

            assertThat(whatIsDelivered()).isEmpty();
        }
    }

    @Nested
    @DisplayName("the surface closing")
    class TheSurfaceClosing {

        @Test
        @DisplayName("is one close for each window showing, in the order of the root's pane")
        void isOneCloseForEachWindow() {
            theSurfaceReports(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore());

            assertThat(whatIsDelivered()).containsExactly(
                    anEvent(ScreenEventKind.CLOSE, lower, new ScreenEventDetail.NothingMore()),
                    anEvent(ScreenEventKind.CLOSE, upper, new ScreenEventDetail.NothingMore()));
        }

        @Test
        @DisplayName("with one window showing is one close")
        void withOneWindowIsOneClose() {
            showing.remove(upper);

            theSurfaceReports(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore());

            assertThat(whatIsDelivered()).containsExactly(
                    anEvent(ScreenEventKind.CLOSE, lower, new ScreenEventDetail.NothingMore()));
        }

        @Test
        @DisplayName("with no window showing reaches nothing")
        void withNoWindowShowingReachesNothing() {
            showing.clear();

            theSurfaceReports(ScreenEventKind.CLOSE, new ScreenEventDetail.NothingMore());

            assertThat(whatIsDelivered()).isEmpty();
        }
    }

    @ParameterizedTest(name = "a {0} names no window and reaches nothing")
    @EnumSource(value = ScreenEventKind.class, names = {"RESIZE", "OFFSET"})
    @DisplayName("the surface never moves or resizes one of its windows")
    void aResizeOrMoveReachesNothing(ScreenEventKind kind) {
        theSurfaceReports(kind, new ScreenEventDetail.At(10, 10));

        assertThat(whatIsDelivered()).isEmpty();
    }

    @Test
    @DisplayName("events are delivered in the order they were reported")
    void eventsKeepTheirOrder() {
        theSurfaceReports(ScreenEventKind.KEY, new ScreenEventDetail.Typed('x'));
        thePointer(ScreenEventKind.MOVE, 20, 30);
        theSurfaceReports(ScreenEventKind.KEY, new ScreenEventDetail.Typed('y'));

        assertThat(whatIsDelivered()).extracting(ScreenEvent::kind).containsExactly(
                ScreenEventKind.KEY, ScreenEventKind.MOVE, ScreenEventKind.KEY);
    }

    @Test
    @DisplayName("what is delivered once is not delivered again")
    void deliveredOnce() {
        thePointer(ScreenEventKind.MOVE, 20, 30);
        whatIsDelivered();

        assertThat(whatIsDelivered()).isEmpty();
    }
}
