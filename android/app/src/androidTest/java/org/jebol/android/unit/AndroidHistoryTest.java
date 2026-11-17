package org.jebol.android.unit;

import org.jebol.adapter.android.AndroidHistory;
import org.jebol.domain.host.History;
import org.jebol.domain.host.HistoryEntry;
import org.jebol.domain.host.Navigation;
import org.jebol.domain.host.ScreenEventQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidHistoryTest {

    private final List<Boolean> backWanted = new ArrayList<>();
    private final AndroidHistory history = new AndroidHistory(backWanted::add);
    private final Navigation navigation = new Navigation(history, new History(() -> 0), new ScreenEventQueue());

    @Test
    @DisplayName("Back is not taken at the first place")
    void leavesBackAtTheFirstPlace() {
        history.replace(new HistoryEntry(0, "first"));

        assertThat(history.isBackWanted()).isFalse();
        assertThat(history.currentAddress()).isEqualTo("first");
    }

    @Test
    @DisplayName("Back is taken once a push leaves a place behind the current one")
    void takesBackAfterAPush() {
        history.replace(new HistoryEntry(0, "first"));
        history.push(new HistoryEntry(1, "second"));

        assertThat(history.isBackWanted()).isTrue();
        assertThat(backWanted).endsWith(true);
        assertThat(history.currentAddress()).isEqualTo("second");
    }

    @Test
    @DisplayName("a script's Back to the first place gives Back up again")
    void givesBackUpAtTheFirstPlaceAgain() {
        history.replace(new HistoryEntry(0, "first"));
        history.push(new HistoryEntry(1, "second"));

        history.back(new HistoryEntry(0, "first"), navigation);

        assertThat(history.isBackWanted()).isFalse();
        assertThat(backWanted).endsWith(false);
        assertThat(history.currentAddress()).isEqualTo("first");
    }

    @Test
    @DisplayName("the operator's Back moves to the place behind, and at the first place gives Back up")
    void followsTheOperatorsBack() {
        history.replace(new HistoryEntry(0, "first"));
        history.push(new HistoryEntry(1, "second"));
        history.push(new HistoryEntry(2, "third"));

        history.followOperatorBack();
        assertThat(history.currentAddress()).isEqualTo("second");
        assertThat(history.isBackWanted()).isTrue();

        history.followOperatorBack();
        assertThat(history.currentAddress()).isEqualTo("first");
        assertThat(history.isBackWanted()).isFalse();
    }

    @Test
    @DisplayName("a push after going back forgets the places that were ahead")
    void forgetsWhatWasAhead() {
        history.replace(new HistoryEntry(0, "first"));
        history.push(new HistoryEntry(1, "second"));
        history.followOperatorBack();
        history.push(new HistoryEntry(1, "other"));

        assertThat(history.currentAddress()).isEqualTo("other");
        history.followOperatorBack();
        assertThat(history.currentAddress()).isEqualTo("first");
    }

    @Test
    @DisplayName("Back is taken at the first place while unsaved work is flagged, and given up when it is not")
    void takesBackWhileGuarding() {
        history.replace(new HistoryEntry(0, "first"));

        history.guardLeaving(true);
        assertThat(history.isBackWanted()).isTrue();
        assertThat(history.isGuarding()).isTrue();

        history.guardLeaving(false);
        assertThat(history.isBackWanted()).isFalse();
    }

    @Test
    @DisplayName("the listener hears only a change, never the same answer twice running")
    void tellsOnlyChanges() {
        history.replace(new HistoryEntry(0, "first"));
        history.push(new HistoryEntry(1, "a"));
        history.push(new HistoryEntry(2, "b"));
        history.guardLeaving(true);

        assertThat(backWanted).containsExactly(true);
    }
}
