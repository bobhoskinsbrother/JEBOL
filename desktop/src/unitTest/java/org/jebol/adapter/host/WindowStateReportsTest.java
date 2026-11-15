package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Frame;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WindowStateReportsTest {

    private static final int NORMAL = Frame.NORMAL;
    private static final int ICONIFIED = Frame.ICONIFIED;
    private static final int MAXIMIZED = Frame.MAXIMIZED_BOTH;

    private long now = 1_000_000;
    private final WindowStateReports reports = new WindowStateReports(() -> now);

    private static WindowStateReports.Report resize(int wide, int high) {
        return new WindowStateReports.Report(ScreenEventType.RESIZE, new ScreenEventDetail.At(wide, high));
    }

    private static WindowStateReports.Report said(ScreenEventType kind) {
        return new WindowStateReports.Report(kind, new ScreenEventDetail.NothingMore());
    }

    private void openedAt(int wide, int high) {
        reports.resized(wide, high);
        now += 10_000;
    }

    private void later() {
        now += 10_000;
    }

    @Nested
    @DisplayName("each change of state says what R3's WM_SIZE says")
    class EachChange {

        @Test
        @DisplayName("normal to iconified is minimize alone, at once")
        void minimises() {
            openedAt(200, 100);

            assertThat(reports.stateChanged(NORMAL, ICONIFIED, 200, 100))
                    .containsExactly(said(ScreenEventType.MINIMIZE));
        }

        @Test
        @DisplayName("iconified to normal is resize then restore at once, though the size did not change")
        void restoresFromIconified() {
            openedAt(200, 100);
            reports.stateChanged(NORMAL, ICONIFIED, 200, 100);

            assertThat(reports.stateChanged(ICONIFIED, NORMAL, 200, 100))
                    .containsExactly(resize(200, 100), said(ScreenEventType.RESTORE));
        }

        @Test
        @DisplayName("normal to maximised is resize then maximize, once the resize comes")
        void maximises() {
            openedAt(200, 100);

            assertThat(reports.stateChanged(NORMAL, MAXIMIZED, 200, 100)).isEmpty();
            assertThat(reports.resized(1024, 740))
                    .containsExactly(resize(1024, 740), said(ScreenEventType.MAXIMIZE));
        }

        @Test
        @DisplayName("maximised to normal is resize then restore, once the resize comes")
        void restoresFromMaximised() {
            openedAt(200, 100);
            reports.stateChanged(NORMAL, MAXIMIZED, 200, 100);
            reports.resized(1024, 740);
            later();

            assertThat(reports.stateChanged(MAXIMIZED, NORMAL, 1024, 740)).isEmpty();
            assertThat(reports.resized(200, 100))
                    .containsExactly(resize(200, 100), said(ScreenEventType.RESTORE));
        }

        @Test
        @DisplayName("maximised to iconified over maximised is minimize alone")
        void minimisesAMaximisedWindow() {
            openedAt(1024, 740);

            assertThat(reports.stateChanged(MAXIMIZED, ICONIFIED | MAXIMIZED, 1024, 740))
                    .containsExactly(said(ScreenEventType.MINIMIZE));
        }

        @Test
        @DisplayName("iconified over maximised back to maximised is resize then maximize")
        void unminimisesToMaximised() {
            openedAt(1024, 740);

            assertThat(reports.stateChanged(ICONIFIED | MAXIMIZED, MAXIMIZED, 1024, 740))
                    .containsExactly(resize(1024, 740), said(ScreenEventType.MAXIMIZE));
        }

        @Test
        @DisplayName("a report that changes nothing says nothing")
        void saysNothingForNoChange() {
            openedAt(200, 100);

            assertThat(reports.stateChanged(NORMAL, NORMAL, 200, 100)).isEmpty();
            assertThat(reports.takeOverdue(200, 100)).isEmpty();
        }
    }

    @Nested
    @DisplayName("one resize, not two, whichever the toolkit reports first")
    class OneResize {

        @Test
        @DisplayName("the state first: the resize after it is said once, before maximize")
        void stateFirst() {
            openedAt(200, 100);
            reports.stateChanged(NORMAL, MAXIMIZED, 200, 100);

            assertThat(reports.resized(1024, 740))
                    .containsExactly(resize(1024, 740), said(ScreenEventType.MAXIMIZE));
            assertThat(reports.resized(1024, 740)).isEmpty();
            assertThat(reports.takeOverdue(1024, 740)).isEmpty();
        }

        @Test
        @DisplayName("the resize just before: when no other comes, the state adds only maximize")
        void resizeJustBefore() {
            openedAt(200, 100);

            assertThat(reports.resized(1024, 740)).containsExactly(resize(1024, 740));
            now += 50;
            assertThat(reports.stateChanged(NORMAL, MAXIMIZED, 1024, 740)).isEmpty();
            now += WindowStateReports.MILLISECONDS_A_STATE_WAITS_FOR_ITS_RESIZE;
            assertThat(reports.takeOverdue(1024, 740)).containsExactly(said(ScreenEventType.MAXIMIZE));
        }

        @Test
        @DisplayName("a resize that settles a window just opened is not taken for a later state's")
        void aSettlingResizeIsNotTheStatesResize() {
            openedAt(200, 100);
            reports.resized(202, 100);
            now += 100;
            reports.stateChanged(NORMAL, MAXIMIZED, 202, 100);

            assertThat(reports.resized(1024, 740))
                    .containsExactly(resize(1024, 740), said(ScreenEventType.MAXIMIZE));
        }

        @Test
        @DisplayName("if no resize ever comes and none came just before, the overdue report says resize then maximize")
        void noResizeEverComes() {
            openedAt(200, 100);
            reports.stateChanged(NORMAL, MAXIMIZED, 200, 100);

            assertThat(reports.takeOverdue(200, 100))
                    .containsExactly(resize(200, 100), said(ScreenEventType.MAXIMIZE));
            assertThat(reports.takeOverdue(200, 100)).isEmpty();
        }

        @Test
        @DisplayName("a drag long before a state change is not taken as its resize")
        void anOldDragIsNotTheResize() {
            openedAt(200, 100);
            reports.resized(300, 200);
            later();
            reports.stateChanged(NORMAL, MAXIMIZED, 300, 200);

            assertThat(reports.takeOverdue(300, 200))
                    .containsExactly(resize(300, 200), said(ScreenEventType.MAXIMIZE));
        }

        @Test
        @DisplayName("a minimise in between drops a maximize still waiting")
        void minimiseDropsAWaitingState() {
            openedAt(200, 100);
            reports.stateChanged(NORMAL, MAXIMIZED, 200, 100);

            assertThat(reports.stateChanged(MAXIMIZED, ICONIFIED | MAXIMIZED, 200, 100))
                    .containsExactly(said(ScreenEventType.MINIMIZE));
            assertThat(reports.takeOverdue(200, 100)).isEmpty();
        }
    }

    @Nested
    @DisplayName("a minimised window shown again by hiding and showing it, which is what a compositor honours")
    class Remapping {

        @Test
        @DisplayName("every report while it is remapped is held back")
        void holdsBackTheFlipsOnTheWay() {
            openedAt(200, 100);
            reports.stateChanged(NORMAL, ICONIFIED, 200, 100);
            reports.beginRemap();

            assertThat(List.of(
                    reports.stateChanged(ICONIFIED, NORMAL, 200, 100),
                    reports.stateChanged(NORMAL, ICONIFIED, 200, 100),
                    reports.stateChanged(ICONIFIED, NORMAL, 200, 100),
                    reports.resized(210, 100)))
                    .containsOnly(List.of());
        }

        @Test
        @DisplayName("and when it settles normal, it says resize then restore")
        void saysRestoreWhenItSettles() {
            openedAt(200, 100);
            reports.stateChanged(NORMAL, ICONIFIED, 200, 100);
            reports.beginRemap();
            reports.stateChanged(ICONIFIED, NORMAL, 200, 100);

            assertThat(reports.endRemap(NORMAL, 200, 100))
                    .containsExactly(resize(200, 100), said(ScreenEventType.RESTORE));
        }

        @Test
        @DisplayName("settled maximised, it says resize then maximize")
        void saysMaximizeWhenItSettlesMaximised() {
            openedAt(200, 100);
            reports.beginRemap();

            assertThat(reports.endRemap(MAXIMIZED, 1024, 740))
                    .containsExactly(resize(1024, 740), said(ScreenEventType.MAXIMIZE));
        }

        @Test
        @DisplayName("settled minimised after all, it says nothing, since nothing changed")
        void saysNothingIfStillMinimised() {
            openedAt(200, 100);
            reports.stateChanged(NORMAL, ICONIFIED, 200, 100);
            reports.beginRemap();

            assertThat(reports.endRemap(ICONIFIED, 200, 100)).isEmpty();
        }

        @Test
        @DisplayName("and reports flow again once it has settled")
        void reportsFlowAfterwards() {
            openedAt(200, 100);
            reports.beginRemap();
            reports.endRemap(NORMAL, 200, 100);

            assertThat(reports.stateChanged(NORMAL, ICONIFIED, 200, 100))
                    .containsExactly(said(ScreenEventType.MINIMIZE));
        }
    }

    @Nested
    @DisplayName("an operator's drag")
    class ADrag {

        @Test
        @DisplayName("is a resize for each new size and nothing for the same one again")
        void isAResizeEachTime() {
            assertThat(List.of(reports.resized(200, 100), reports.resized(210, 100), reports.resized(210, 100)))
                    .containsExactly(List.of(resize(200, 100)), List.of(resize(210, 100)), List.of());
        }
    }
}
