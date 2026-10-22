package org.jebol.domain.host;

import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.Raised;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TheHistorysRulesTest {

    private long millisecondsNow = 1_000_000;

    private final History history = new History(() -> millisecondsNow);

    private Place place(String source) {
        return Place.of(Transcoder.transcode(source).values().orElseThrow().listItems().getFirst());
    }

    private String errorIdRaisedBy(Runnable attempt) {
        try {
            attempt.run();
        } catch (Raised raised) {
            return raised.error().errorId();
        }
        throw new AssertionError("nothing was raised");
    }

    private void makeChanges(int count) {
        for (int made = 0; made < count; made++) {
            history.replace(place("[p " + made + "]"));
        }
    }

    @Nested
    @DisplayName("before the app moves")
    class BeforeTheAppMoves {

        @Test
        @DisplayName("a new history is at the start, at entry nought, unused, with nothing refused")
        void aNewHistoryIsAtTheStart() {
            assertThat(history.current()).isEqualTo(Place.start());
            assertThat(history.position()).isZero();
            assertThat(history.isUsedByApp()).isFalse();
            assertThat(history.refusedAddress()).isEmpty();
        }

        @Test
        @DisplayName("a fresh load starts where its address points")
        void aFreshLoadStartsWhereTheAddressPoints() {
            history.startAt(0, "survey%203");

            assertThat(history.current()).isEqualTo(place("[survey 3]"));
            assertThat(history.refusedAddress()).isEmpty();
        }

        @Test
        @DisplayName("a reload keeps the index the browser kept, so Back still has somewhere to go")
        void aReloadKeepsItsIndex() {
            history.startAt(3, "b");

            assertThat(history.position()).isEqualTo(3);
            assertThat(history.backTarget()).contains(new HistoryEntry(2, ""));
        }

        @Test
        @DisplayName("an unreadable address starts at the start and is kept as it was given")
        void anUnreadableAddressStartsAtTheStart() {
            history.startAt(0, "%zz");

            assertThat(history.current()).isEqualTo(Place.start());
            assertThat(history.refusedAddress()).contains("%zz");
        }

        @Test
        @DisplayName("a readable address after an unreadable one clears what was refused")
        void aReadableAddressClearsTheRefusal() {
            history.startAt(0, "%zz");
            history.startAt(0, "a");

            assertThat(history.refusedAddress()).isEmpty();
        }

        @Test
        @DisplayName("a negative index is no entry at all")
        void aNegativeIndexIsRefused() {
            assertThatThrownBy(() -> history.startAt(-1, "a")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> history.arriveAt(-1, Place.start())).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("going somewhere")
    class GoingSomewhere {

        @Test
        @DisplayName("somewhere new adds an entry after the current one")
        void somewhereNewAddsAnEntry() {
            assertThat(history.goTo(place("survey"))).contains(new HistoryEntry(1, "survey"));
            assertThat(history.position()).isEqualTo(1);
            assertThat(history.current()).isEqualTo(place("survey"));
            assertThat(history.isUsedByApp()).isTrue();
        }

        @Test
        @DisplayName("the place already current adds nothing, and the app has still used its history")
        void theCurrentPlaceAddsNothing() {
            history.goTo(place("survey"));

            assertThat(history.goTo(place("[survey]"))).isEmpty();
            assertThat(history.position()).isEqualTo(1);
        }

        @Test
        @DisplayName("going to the current place marks a history unused before as used")
        void goingToTheStartMarksUsed() {
            assertThat(history.goTo(Place.start())).isEmpty();
            assertThat(history.isUsedByApp()).isTrue();
        }

        @Test
        @DisplayName("the same place with different data is a move")
        void differentDataIsAMove() {
            history.goTo(place("[survey 3]"));

            assertThat(history.goTo(place("[survey 4]"))).contains(new HistoryEntry(2, "survey%204"));
        }

        @Test
        @DisplayName("somewhere new after going back drops every entry that was ahead")
        void somewhereNewDropsWhatWasAhead() {
            history.goTo(place("a"));
            history.goTo(place("b"));
            history.arriveAt(1, place("a"));

            history.goTo(place("c"));

            assertThat(history.position()).isEqualTo(2);
            assertThat(history.current()).isEqualTo(place("c"));
            assertThat(history.forwardTarget()).isEmpty();
            assertThat(history.backTarget()).contains(new HistoryEntry(1, "a"));
        }

        @Test
        @DisplayName("replacing changes the current entry and nothing else")
        void replacingChangesOnlyTheCurrentEntry() {
            history.goTo(place("a"));
            history.goTo(place("b"));
            history.arriveAt(1, place("a"));

            assertThat(history.replace(place("z"))).isEqualTo(new HistoryEntry(1, "z"));

            assertThat(history.position()).isEqualTo(1);
            assertThat(history.current()).isEqualTo(place("z"));
            assertThat(history.forwardTarget()).contains(new HistoryEntry(2, "b"));
            assertThat(history.isUsedByApp()).isTrue();
        }
    }

    @Nested
    @DisplayName("back and forward")
    class BackAndForward {

        @Test
        @DisplayName("at the first entry there is no back, and at the last no forward")
        void theEndsHaveNowhereToGo() {
            assertThat(history.backTarget()).isEmpty();
            assertThat(history.forwardTarget()).isEmpty();
            assertThat(history.operatorBackTarget()).isEmpty();
            assertThat(history.operatorForwardTarget()).isEmpty();
        }

        @Test
        @DisplayName("back names the entry before, and does not move until the screen says it arrived")
        void backNamesTheEntryBefore() {
            history.goTo(place("a"));

            assertThat(history.backTarget()).contains(new HistoryEntry(0, ""));
            assertThat(history.position()).isEqualTo(1);
        }

        @Test
        @DisplayName("forward names the entry after")
        void forwardNamesTheEntryAfter() {
            history.goTo(place("a"));
            history.arriveAt(0, Place.start());

            assertThat(history.forwardTarget()).contains(new HistoryEntry(1, "a"));
            assertThat(history.operatorForwardTarget()).contains(new HistoryEntry(1, "a"));
        }

        @Test
        @DisplayName("an arrival before the current entry is back, after it forward, and at it nothing")
        void anArrivalSaysWhichWay() {
            history.goTo(place("a"));
            history.goTo(place("b"));

            assertThat(history.arriveAt(1, place("a"))).contains(ScreenEventType.BACK);
            assertThat(history.arriveAt(2, place("b"))).contains(ScreenEventType.FORWARD);
            assertThat(history.arriveAt(2, place("b"))).isEmpty();
            assertThat(history.position()).isEqualTo(2);
        }

        @Test
        @DisplayName("an arrival takes the place it reports, which after a reload is the only way to know it")
        void anArrivalTakesItsPlace() {
            history.startAt(3, "c");

            history.arriveAt(2, place("b"));

            assertThat(history.current()).isEqualTo(place("b"));
            assertThat(history.forwardTarget()).contains(new HistoryEntry(3, "c"));
        }

        @Test
        @DisplayName("an arrival past the last entry is a new entry the operator made")
        void anArrivalPastTheEndIsANewEntry() {
            assertThat(history.arriveAt(1, place("typed"))).contains(ScreenEventType.FORWARD);
            assertThat(history.current()).isEqualTo(place("typed"));
            assertThat(history.backTarget()).contains(new HistoryEntry(0, ""));
        }
    }

    @Nested
    @DisplayName("the rate")
    class TheRate {

        @Test
        @DisplayName("a hundred changes in thirty seconds are allowed")
        void aHundredAreAllowed() {
            assertThat(History.MOST_CHANGES_PER_WINDOW).isEqualTo(100);
            assertThat(History.CHANGE_WINDOW_MILLISECONDS).isEqualTo(30_000);

            makeChanges(100);

            assertThat(history.current()).isEqualTo(place("[p 99]"));
        }

        @Test
        @DisplayName("ninety nine leave room for one more")
        void ninetyNineLeaveRoom() {
            makeChanges(99);

            assertThat(history.goTo(place("one-more"))).isPresent();
        }

        @Test
        @DisplayName("the hundred and first is refused as a limit, and changes nothing")
        void theHundredAndFirstIsRefused() {
            makeChanges(100);

            assertThat(errorIdRaisedBy(() -> history.goTo(place("too-many")))).isEqualTo("size-limit");
            assertThat(errorIdRaisedBy(() -> history.replace(place("too-many")))).isEqualTo("size-limit");
            assertThat(history.current()).isEqualTo(place("[p 99]"));
            assertThat(history.position()).isZero();
        }

        @Test
        @DisplayName("back and forward asked for by the app count too")
        void theAppsBackAndForwardCount() {
            history.goTo(place("a"));
            for (int asked = 1; asked < 100; asked++) {
                history.backTarget();
            }

            assertThat(errorIdRaisedBy(history::backTarget)).isEqualTo("size-limit");
        }

        @Test
        @DisplayName("forward asked for by the app counts, and past the rate is refused")
        void theAppsForwardCounts() {
            history.goTo(place("a"));
            history.arriveAt(0, Place.start());
            for (int asked = 1; asked < 100; asked++) {
                history.forwardTarget();
            }

            assertThat(errorIdRaisedBy(history::forwardTarget)).isEqualTo("size-limit");
        }

        @Test
        @DisplayName("back and forward that go nowhere count for nothing")
        void goingNowhereCountsNothing() {
            for (int asked = 0; asked < 200; asked++) {
                history.backTarget();
            }

            assertThat(history.goTo(place("a"))).isPresent();
        }

        @Test
        @DisplayName("going to the current place is no change and is not counted")
        void stayingIsNotCounted() {
            history.goTo(place("a"));
            for (int asked = 0; asked < 200; asked++) {
                history.goTo(place("a"));
            }

            assertThat(history.goTo(place("b"))).isPresent();
        }

        @Test
        @DisplayName("the operator's own moves are never counted")
        void theOperatorIsNotCounted() {
            history.goTo(place("a"));
            makeChanges(99);

            assertThat(history.operatorBackTarget()).contains(new HistoryEntry(0, ""));
            history.arriveAt(0, Place.start());
            assertThat(history.operatorForwardTarget()).isPresent();
        }

        @Test
        @DisplayName("a change one millisecond inside the window still counts")
        void insideTheWindowStillCounts() {
            makeChanges(100);
            millisecondsNow += History.CHANGE_WINDOW_MILLISECONDS - 1;

            assertThat(errorIdRaisedBy(() -> history.goTo(place("late")))).isEqualTo("size-limit");
        }

        @Test
        @DisplayName("a change exactly thirty seconds old no longer counts")
        void exactlyTheWindowNoLongerCounts() {
            makeChanges(100);
            millisecondsNow += History.CHANGE_WINDOW_MILLISECONDS;

            assertThat(history.goTo(place("later"))).isPresent();
        }

        @Test
        @DisplayName("the window slides: only the changes that left it make room")
        void theWindowSlides() {
            makeChanges(50);
            millisecondsNow += 10_000;
            makeChanges(50);
            millisecondsNow += History.CHANGE_WINDOW_MILLISECONDS - 10_000;

            assertThat(history.goTo(place("room"))).isPresent();
            makeChanges(49);
            assertThat(errorIdRaisedBy(() -> history.goTo(place("full")))).isEqualTo("size-limit");
        }
    }

    @Test
    @DisplayName("an app marks its history used by asking about it")
    void askingMarksUsed() {
        history.markUsed();

        assertThat(history.isUsedByApp()).isTrue();
        assertThat(history.current()).isEqualTo(Place.start());
        assertThat(Optional.of(history.position())).contains(0);
    }
}
