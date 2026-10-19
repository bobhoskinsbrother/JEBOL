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

    private record Reported(ScreenEventType kind, ScreenEventDetail detail) {
    }
    private List<GobValue> showing;
    private GobValue lower;
    private GobValue upper;

    @BeforeEach
    void openTwoOverlappingWindows() {
        surface = new WindowsOnOneSurface();
        lower = makeAWindowAt(10, 20, 100, 100);
        upper = makeAWindowAt(60, 70, 100, 100);
        showing = new ArrayList<>(List.of(lower, upper));
    }

    private GobValue makeAWindowAt(int across, int down, int wide, int high) {
        GobValue window = GobValue.empty();
        window.storage().setTheOffset(PairValue.of(across, down));
        window.storage().size(PairValue.of(wide, high));
        return window;
    }

    private void report(ScreenEventType kind, ScreenEventDetail detail) {
        reported.add(new Reported(kind, detail));
    }

    private void reportThePointer(ScreenEventType kind, int across, int down) {
        report(kind, new ScreenEventDetail.At(across, down));
    }

    private List<ScreenEvent> deliverWhatWasReported() {
        List<ScreenEvent> delivered = new ArrayList<>();
        for (Reported each : reported) {
            delivered.addAll(surface.address(each.kind(), each.detail(), List.copyOf(showing)));
        }
        reported.clear();
        return delivered;
    }

    private ScreenEvent makeAnEvent(ScreenEventType kind, GobValue window, int across, int down) {
        return new ScreenEvent(kind, window, new ScreenEventDetail.At(across, down));
    }

    private ScreenEvent makeAnEvent(ScreenEventType kind, GobValue window, ScreenEventDetail detail) {
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
        void belongsToTheWindowItIsOver(int across, int down, int inAcross, int inDown) {
            reportThePointer(ScreenEventType.DOWN, across, down);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(ScreenEventType.DOWN, lower, inAcross, inDown));
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
        void reachesNothingOverNoWindow(int across, int down) {
            reportThePointer(ScreenEventType.DOWN, across, down);

            assertThat(deliverWhatWasReported()).isEmpty();
        }

        @ParameterizedTest(name = "at {0}x{1}, where both windows are, belongs to the upper as {2}x{3}")
        @CsvSource({
                "60,  70,  0,  0",
                "109, 119, 49, 49",
                "80,  90,  20, 20",
        })
        @DisplayName("where two windows overlap belongs to the one later in the root's pane")
        void belongsToTheUpperWhereTwoOverlap(int across, int down, int inAcross, int inDown) {
            reportThePointer(ScreenEventType.DOWN, across, down);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(ScreenEventType.DOWN, upper, inAcross, inDown));
        }

        @Test
        @DisplayName("and when the pane's order is the other way round, the other window is on top")
        void followsThePanesOrderToTheTop() {
            showing = new ArrayList<>(List.of(upper, lower));

            reportThePointer(ScreenEventType.DOWN, 80, 90);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(ScreenEventType.DOWN, lower, 70, 70));
        }

        @Test
        @DisplayName("with no window showing reaches nothing")
        void reachesNothingWithNoWindowShowing() {
            showing.clear();

            reportThePointer(ScreenEventType.DOWN, 50, 50);

            assertThat(deliverWhatWasReported()).isEmpty();
        }

        @Test
        @DisplayName("a window of no size covers no point at all, not even its own offset")
        void missesAWindowOfNoSize() {
            GobValue empty = makeAWindowAt(300, 300, 0, 0);
            showing.add(empty);

            reportThePointer(ScreenEventType.DOWN, 300, 300);

            assertThat(deliverWhatWasReported()).isEmpty();
        }

        @Test
        @DisplayName("a window placed partly off the surface still takes a press on the part that shows")
        void reachesAWindowPartlyOffTheSurface() {
            GobValue offTheEdge = makeAWindowAt(-50, -50, 100, 100);
            showing = new ArrayList<>(List.of(offTheEdge));

            reportThePointer(ScreenEventType.DOWN, 0, 0);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(ScreenEventType.DOWN, offTheEdge, 50, 50));
        }

        @Test
        @DisplayName("is judged against where the windows stand when it is addressed, not when it was reported")
        void isJudgedWhenAddressed() {
            reportThePointer(ScreenEventType.DOWN, 300, 300);
            lower.storage().setTheOffset(PairValue.of(250, 250));

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(ScreenEventType.DOWN, lower, 50, 50));
        }
    }

    @Nested
    @DisplayName("while a press is held")
    class WhileAPressIsHeld {

        @Test
        @DisplayName("a move off every window still belongs to the window the press began in, counted from it")
        void givesAMoveOffEveryWindowToThePressedWindow() {
            reportThePointer(ScreenEventType.DOWN, 20, 30);
            reportThePointer(ScreenEventType.MOVE, 0, 0);

            assertThat(deliverWhatWasReported()).containsExactly(
                    makeAnEvent(ScreenEventType.DOWN, lower, 10, 10),
                    makeAnEvent(ScreenEventType.MOVE, lower, -10, -20));
        }

        @Test
        @DisplayName("a move over another window, and the release there, still belong to the pressed window")
        void givesAMoveAndReleaseOverAnotherWindowToThePressedWindow() {
            reportThePointer(ScreenEventType.DOWN, 20, 30);
            reportThePointer(ScreenEventType.MOVE, 150, 150);
            reportThePointer(ScreenEventType.UP, 150, 150);

            assertThat(deliverWhatWasReported()).containsExactly(
                    makeAnEvent(ScreenEventType.DOWN, lower, 10, 10),
                    makeAnEvent(ScreenEventType.MOVE, lower, 140, 130),
                    makeAnEvent(ScreenEventType.UP, lower, 140, 130));
        }

        @Test
        @DisplayName("and once released, the next move belongs to the window under the pointer again")
        void returnsToTheWindowUnderThePointerAfterTheRelease() {
            reportThePointer(ScreenEventType.DOWN, 20, 30);
            reportThePointer(ScreenEventType.UP, 150, 150);
            reportThePointer(ScreenEventType.MOVE, 150, 150);

            assertThat(deliverWhatWasReported()).last().isEqualTo(makeAnEvent(ScreenEventType.MOVE, upper, 90, 80));
        }

        @Test
        @DisplayName("a press held over a window that then closes holds nothing, so the window under the pointer hears")
        void dropsThePressWhenThePressedWindowCloses() {
            reportThePointer(ScreenEventType.DOWN, 20, 30);
            deliverWhatWasReported();
            showing.remove(lower);

            reportThePointer(ScreenEventType.MOVE, 150, 150);
            reportThePointer(ScreenEventType.MOVE, 0, 0);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(ScreenEventType.MOVE, upper, 90, 80));
        }

        @Test
        @DisplayName("a press on nothing holds nothing, so a move over a window afterwards belongs to that window")
        void holdsNothingForAPressOnNothing() {
            reportThePointer(ScreenEventType.DOWN, 0, 0);
            reportThePointer(ScreenEventType.MOVE, 20, 30);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(ScreenEventType.MOVE, lower, 10, 10));
        }
    }

    @Nested
    @DisplayName("with no press held")
    class WithNoPressHeld {

        @ParameterizedTest(name = "a {0} over the upper window belongs to it")
        @EnumSource(value = ScreenEventType.class, names = {"MOVE", "UP"})
        @DisplayName("a move or a release belongs to the topmost window under the pointer")
        void belongsToTheWindowUnderThePointer(ScreenEventType kind) {
            reportThePointer(kind, 80, 90);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(kind, upper, 20, 20));
        }

        @ParameterizedTest(name = "a {0} over no window reaches nothing")
        @EnumSource(value = ScreenEventType.class, names = {"MOVE", "UP"})
        @DisplayName("and over no window reaches nothing")
        void reachesNothingOverNoWindow(ScreenEventType kind) {
            reportThePointer(kind, 9, 19);

            assertThat(deliverWhatWasReported()).isEmpty();
        }
    }

    @Nested
    @DisplayName("a key")
    class AKey {

        private final ScreenEventDetail typedA = new ScreenEventDetail.Typed('a');

        @ParameterizedTest(name = "a {0} before any press belongs to the topmost window")
        @EnumSource(value = ScreenEventType.class, names = {"KEY", "KEY_UP", "CONTROL", "CONTROL_UP"})
        @DisplayName("before any press belongs to the topmost window, which a desktop gives a new window")
        void goesToTheTopmostBeforeAnyPress(ScreenEventType kind) {
            report(kind, typedA);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(kind, upper, typedA));
        }

        @Test
        @DisplayName("after a press in the lower window belongs to the lower window")
        void goesToThePressedWindowAfterAPress() {
            reportThePointer(ScreenEventType.DOWN, 20, 30);
            reportThePointer(ScreenEventType.UP, 20, 30);
            report(ScreenEventType.KEY, typedA);

            assertThat(deliverWhatWasReported()).last().isEqualTo(makeAnEvent(ScreenEventType.KEY, lower, typedA));
        }

        @Test
        @DisplayName("a named key follows the press too")
        void followsThePressWhenNamed() {
            ScreenEventDetail left = new ScreenEventDetail.NamedKey("left");
            reportThePointer(ScreenEventType.DOWN, 20, 30);
            report(ScreenEventType.CONTROL, left);

            assertThat(deliverWhatWasReported()).last().isEqualTo(makeAnEvent(ScreenEventType.CONTROL, lower, left));
        }

        @Test
        @DisplayName("a press on nothing leaves the keyboard where it was")
        void staysAfterAPressOnNothing() {
            reportThePointer(ScreenEventType.DOWN, 20, 30);
            reportThePointer(ScreenEventType.UP, 20, 30);
            reportThePointer(ScreenEventType.DOWN, 0, 0);
            report(ScreenEventType.KEY, typedA);

            assertThat(deliverWhatWasReported()).last().isEqualTo(makeAnEvent(ScreenEventType.KEY, lower, typedA));
        }

        @Test
        @DisplayName("once the window pressed in has closed belongs to the topmost window again")
        void onceThePressedWindowClosesBelongsToTheTopmost() {
            GobValue third = makeAWindowAt(400, 400, 50, 50);
            showing.add(third);
            reportThePointer(ScreenEventType.DOWN, 20, 30);
            deliverWhatWasReported();
            showing.remove(lower);

            report(ScreenEventType.KEY, typedA);

            assertThat(deliverWhatWasReported()).containsExactly(makeAnEvent(ScreenEventType.KEY, third, typedA));
        }

        @Test
        @DisplayName("with no window showing reaches nothing")
        void reachesNothingWithNoWindowShowing() {
            showing.clear();

            report(ScreenEventType.KEY, typedA);

            assertThat(deliverWhatWasReported()).isEmpty();
        }
    }

    @Nested
    @DisplayName("the surface closing")
    class TheSurfaceClosing {

        @Test
        @DisplayName("is one close for each window showing, in the order of the root's pane")
        void isOneCloseForEachWindow() {
            report(ScreenEventType.CLOSE, new ScreenEventDetail.NothingMore());

            assertThat(deliverWhatWasReported()).containsExactly(
                    makeAnEvent(ScreenEventType.CLOSE, lower, new ScreenEventDetail.NothingMore()),
                    makeAnEvent(ScreenEventType.CLOSE, upper, new ScreenEventDetail.NothingMore()));
        }

        @Test
        @DisplayName("with one window showing is one close")
        void closesTheOneWindow() {
            showing.remove(upper);

            report(ScreenEventType.CLOSE, new ScreenEventDetail.NothingMore());

            assertThat(deliverWhatWasReported()).containsExactly(
                    makeAnEvent(ScreenEventType.CLOSE, lower, new ScreenEventDetail.NothingMore()));
        }

        @Test
        @DisplayName("with no window showing reaches nothing")
        void reachesNothingWithNoWindowShowing() {
            showing.clear();

            report(ScreenEventType.CLOSE, new ScreenEventDetail.NothingMore());

            assertThat(deliverWhatWasReported()).isEmpty();
        }
    }

    @ParameterizedTest(name = "a {0} names no window and reaches nothing")
    @EnumSource(value = ScreenEventType.class, names = {"RESIZE", "OFFSET"})
    @DisplayName("the surface never moves or resizes one of its windows")
    void deliversNoResizeOrMove(ScreenEventType kind) {
        report(kind, new ScreenEventDetail.At(10, 10));

        assertThat(deliverWhatWasReported()).isEmpty();
    }

    @Test
    @DisplayName("events are delivered in the order they were reported")
    void keepsTheEventsInOrder() {
        report(ScreenEventType.KEY, new ScreenEventDetail.Typed('x'));
        reportThePointer(ScreenEventType.MOVE, 20, 30);
        report(ScreenEventType.KEY, new ScreenEventDetail.Typed('y'));

        assertThat(deliverWhatWasReported()).extracting(ScreenEvent::type).containsExactly(
                ScreenEventType.KEY, ScreenEventType.MOVE, ScreenEventType.KEY);
    }

    @Test
    @DisplayName("what is delivered once is not delivered again")
    void deliversOnce() {
        reportThePointer(ScreenEventType.MOVE, 20, 30);
        deliverWhatWasReported();

        assertThat(deliverWhatWasReported()).isEmpty();
    }
}
