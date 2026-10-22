package org.jebol.domain.host;

import org.jebol.domain.read.Transcoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationBetweenTheAppAndItsScreenTest {

    private final ScreenEventQueue events = new ScreenEventQueue();

    private Place place(String source) {
        return Place.of(Transcoder.transcode(source).values().orElseThrow().listItems().getFirst());
    }

    private final class ABrowsersHistory implements NavigationPort {

        private final List<String> told = new ArrayList<>();

        @Override
        public void push(HistoryEntry entry) {
            told.add("push " + entry.index() + " " + entry.address());
        }

        @Override
        public void replace(HistoryEntry entry) {
            told.add("replace " + entry.index() + " " + entry.address());
        }

        @Override
        public void back(HistoryEntry target, Navigation arrivals) {
            told.add("back");
        }

        @Override
        public void forward(HistoryEntry target, Navigation arrivals) {
            told.add("forward");
        }

        @Override
        public void guardLeaving(boolean unsavedWork) {
            told.add("guard " + unsavedWork);
        }
    }

    private final class AWindowsHistory implements NavigationPort {

        @Override
        public void push(HistoryEntry entry) {
        }

        @Override
        public void replace(HistoryEntry entry) {
        }

        @Override
        public void back(HistoryEntry target, Navigation arrivals) {
            arrivals.reportArrival(target.index(), target.address());
        }

        @Override
        public void forward(HistoryEntry target, Navigation arrivals) {
            arrivals.reportArrival(target.index(), target.address());
        }

        @Override
        public void guardLeaving(boolean unsavedWork) {
        }
    }

    private final ABrowsersHistory browser = new ABrowsersHistory();

    private Navigation onAPage() {
        return new Navigation(browser, new History(() -> 0L), events);
    }

    private Navigation inAWindow() {
        return new Navigation(new AWindowsHistory(), new History(() -> 0L), events);
    }

    private Optional<ScreenEvent> nextEvent() {
        return events.takeOldest();
    }

    @Nested
    @DisplayName("what the app asks of the screen")
    class WhatTheAppAsks {

        @Test
        @DisplayName("going somewhere new pushes the entry, and answers the place now current")
        void goingPushes() {
            Navigation navigation = onAPage();

            assertThat(navigation.goTo(place("[survey 3]"))).isEqualTo(place("[survey 3]"));

            assertThat(browser.told).containsExactly("push 1 survey%203");
            navigation.takeReports();
            assertThat(nextEvent()).isEmpty();
        }

        @Test
        @DisplayName("going where the app already is tells the screen nothing")
        void goingNowhereTellsNothing() {
            Navigation navigation = onAPage();
            navigation.goTo(place("a"));

            assertThat(navigation.goTo(place("a"))).isEqualTo(place("a"));

            assertThat(browser.told).containsExactly("push 1 a");
        }

        @Test
        @DisplayName("replacing replaces the entry where the app is")
        void replacingReplaces() {
            Navigation navigation = onAPage();
            navigation.goTo(place("a"));

            assertThat(navigation.replace(place("b"))).isEqualTo(place("b"));

            assertThat(browser.told).containsExactly("push 1 a", "replace 1 b");
            navigation.takeReports();
            assertThat(nextEvent()).isEmpty();
        }

        @Test
        @DisplayName("back at the first entry tells the screen nothing")
        void backAtTheStartTellsNothing() {
            Navigation navigation = onAPage();

            navigation.goBack();
            navigation.goForward();

            assertThat(browser.told).isEmpty();
            assertThat(nextEvent()).isEmpty();
        }

        @Test
        @DisplayName("back asks the browser, and waits for the browser to say it arrived")
        void backAsksTheBrowser() {
            Navigation navigation = onAPage();
            navigation.goTo(place("a"));

            navigation.goBack();
            navigation.takeReports();

            assertThat(browser.told).containsExactly("push 1 a", "back");
            assertThat(navigation.current()).isEqualTo(place("a"));
            assertThat(nextEvent()).isEmpty();
        }

        @Test
        @DisplayName("the guard is told to the screen, and kept")
        void theGuardIsTold() {
            Navigation navigation = onAPage();

            navigation.guardLeaving(true);
            assertThat(navigation.isGuarding()).isTrue();
            navigation.guardLeaving(false);

            assertThat(navigation.isGuarding()).isFalse();
            assertThat(browser.told).containsExactly("guard true", "guard false");
        }
    }

    @Nested
    @DisplayName("what the screen reports")
    class WhatTheScreenReports {

        @Test
        @DisplayName("in a window the app's back arrives at once, as a back event naming no window")
        void aWindowsBackArrivesAtOnce() {
            Navigation navigation = inAWindow();
            navigation.goTo(place("a"));

            navigation.goBack();
            navigation.takeReports();

            ScreenEvent arrived = nextEvent().orElseThrow();
            assertThat(arrived.type()).isEqualTo(ScreenEventType.BACK);
            assertThat(arrived.window()).isNull();
            assertThat(arrived.detail()).isEqualTo(new ScreenEventDetail.Arrived(Place.start()));
            assertThat(navigation.current()).isEqualTo(Place.start());
        }

        @Test
        @DisplayName("in a window the app's forward arrives as a forward event carrying the place")
        void aWindowsForwardArrives() {
            Navigation navigation = inAWindow();
            navigation.goTo(place("[a 1]"));
            navigation.goBack();
            navigation.takeReports();
            nextEvent();

            navigation.goForward();
            navigation.takeReports();

            ScreenEvent arrived = nextEvent().orElseThrow();
            assertThat(arrived.type()).isEqualTo(ScreenEventType.FORWARD);
            assertThat(arrived.detail()).isEqualTo(new ScreenEventDetail.Arrived(place("[a 1]")));
        }

        @Test
        @DisplayName("a page's popstate arrives as a move, read from the address it names")
        void aPagesMoveArrives() {
            Navigation navigation = onAPage();
            navigation.goTo(place("a"));
            navigation.goTo(place("b"));

            navigation.reportArrival(1, "a");
            navigation.takeReports();

            assertThat(nextEvent().map(ScreenEvent::type)).contains(ScreenEventType.BACK);
            assertThat(navigation.current()).isEqualTo(place("a"));
        }

        @Test
        @DisplayName("an arrival where the app already is says nothing")
        void anArrivalInPlaceSaysNothing() {
            Navigation navigation = onAPage();
            navigation.goTo(place("a"));

            navigation.reportArrival(1, "a");
            navigation.takeReports();

            assertThat(nextEvent()).isEmpty();
        }

        @Test
        @DisplayName("an arrival at an address that does not read arrives at the start")
        void anUnreadableArrivalIsTheStart() {
            Navigation navigation = onAPage();
            navigation.goTo(place("a"));

            navigation.reportArrival(2, "%zz");
            navigation.takeReports();

            ScreenEvent arrived = nextEvent().orElseThrow();
            assertThat(arrived.type()).isEqualTo(ScreenEventType.FORWARD);
            assertThat(arrived.detail()).isEqualTo(new ScreenEventDetail.Arrived(Place.start()));
        }

        @Test
        @DisplayName("an app that never asked about its history hears nothing, but the history still follows")
        void anAppThatNeverAskedHearsNothing() {
            Navigation navigation = onAPage();

            navigation.reportArrival(1, "typed");
            navigation.takeReports();

            assertThat(nextEvent()).isEmpty();
            assertThat(navigation.current()).isEqualTo(place("typed"));
        }

        @Test
        @DisplayName("asking where the app is marks the history used, so later moves are heard")
        void askingMarksUsed() {
            Navigation navigation = onAPage();
            navigation.current();

            navigation.reportArrival(1, "typed");
            navigation.takeReports();

            assertThat(nextEvent().map(ScreenEvent::type)).contains(ScreenEventType.FORWARD);
        }

        @Test
        @DisplayName("a fresh load starts the history where its address points")
        void aFreshLoadStarts() {
            Navigation navigation = onAPage();

            navigation.reportStart(0, "survey%203");

            assertThat(navigation.current()).isEqualTo(place("[survey 3]"));
            assertThat(navigation.refusedAddress()).isEmpty();
            assertThat(nextEvent()).isEmpty();
        }

        @Test
        @DisplayName("a fresh load from an address that does not read starts at the start, and keeps it")
        void anUnreadableFreshLoad() {
            Navigation navigation = onAPage();

            navigation.reportStart(0, "%5B");

            assertThat(navigation.current()).isEqualTo(Place.start());
            assertThat(navigation.refusedAddress()).contains("%5B");
        }

        @Test
        @DisplayName("the operator's back in a window keeps the rules: nothing at the first entry")
        void theOperatorsBackKeepsTheRules() {
            Navigation navigation = inAWindow();
            navigation.current();

            navigation.reportMoveRequest(ScreenEventType.BACK);
            navigation.takeReports();

            assertThat(nextEvent()).isEmpty();
        }

        @Test
        @DisplayName("the operator's back and forward in a window arrive as events")
        void theOperatorsMovesArrive() {
            Navigation navigation = inAWindow();
            navigation.goTo(place("a"));

            navigation.reportMoveRequest(ScreenEventType.BACK);
            navigation.takeReports();
            navigation.reportMoveRequest(ScreenEventType.FORWARD);
            navigation.takeReports();

            assertThat(nextEvent().map(ScreenEvent::type)).contains(ScreenEventType.BACK);
            assertThat(nextEvent().map(ScreenEvent::type)).contains(ScreenEventType.FORWARD);
            assertThat(navigation.current()).isEqualTo(place("a"));
        }

        @Test
        @DisplayName("a report from another thread waits until the interpreter takes it")
        void aReportWaitsForTheInterpreter() throws Exception {
            Navigation navigation = onAPage();
            navigation.goTo(place("a"));

            CompletableFuture.runAsync(() -> navigation.reportArrival(0, "")).get();

            assertThat(nextEvent()).isEmpty();
            navigation.takeReports();
            assertThat(nextEvent().map(ScreenEvent::type)).contains(ScreenEventType.BACK);
        }

        @Test
        @DisplayName("asking where the app is takes what the screen reported first")
        void currentTakesReportsFirst() {
            Navigation navigation = onAPage();

            navigation.reportStart(0, "b");

            assertThat(navigation.current()).isEqualTo(place("b"));
        }
    }
}
