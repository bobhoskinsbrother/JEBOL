package org.jebol.adapter.host;

import org.jebol.domain.host.Place;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.read.Transcoder;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AWindowsHistoryAndItsCloseTest {

    private final GobValue window = GobValue.empty();

    private final class AnOperatorAskedToConfirm implements LeavingConfirmation {

        private final boolean leaves;
        private int timesAsked;

        AnOperatorAskedToConfirm(boolean leaves) {
            this.leaves = leaves;
        }

        @Override
        public boolean operatorLeaves() {
            timesAsked++;
            return leaves;
        }
    }

    private Place place(String source) {
        return Place.of(Transcoder.transcode(source).values().orElseThrow().listItems().getFirst());
    }

    private List<ScreenEvent> everyEventOn(DesktopScreen screen) {
        List<ScreenEvent> taken = new ArrayList<>();
        for (Optional<ScreenEvent> next = screen.acceptNextEvent(); next.isPresent(); next = screen.acceptNextEvent()) {
            taken.add(next.get());
        }
        return taken;
    }

    private DesktopScreen screenAsking(LeavingConfirmation asking) {
        return DesktopScreen.confirmingWith(new ClasspathFiles(), asking, false);
    }

    @Nested
    @DisplayName("closing a window")
    class ClosingAWindow {

        @Test
        @DisplayName("with no unsaved work the close reaches the script and nobody is asked")
        void withoutTheFlagTheCloseIsPosted() {
            AnOperatorAskedToConfirm operator = new AnOperatorAskedToConfirm(false);
            DesktopScreen screen = screenAsking(operator);

            screen.requestClose(window);

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type).containsExactly(ScreenEventType.CLOSE);
            assertThat(operator.timesAsked).isZero();
        }

        @Test
        @DisplayName("with unsaved work the operator is asked once, and leaving lets the close through")
        void leavingLetsTheCloseThrough() {
            AnOperatorAskedToConfirm operator = new AnOperatorAskedToConfirm(true);
            DesktopScreen screen = screenAsking(operator);
            screen.navigation().guardLeaving(true);

            screen.requestClose(window);

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type).containsExactly(ScreenEventType.CLOSE);
            assertThat(operator.timesAsked).isEqualTo(1);
        }

        @Test
        @DisplayName("with unsaved work, staying keeps the window: nothing reaches the script")
        void stayingKeepsTheWindow() {
            AnOperatorAskedToConfirm operator = new AnOperatorAskedToConfirm(false);
            DesktopScreen screen = screenAsking(operator);
            screen.navigation().guardLeaving(true);

            screen.requestClose(window);

            assertThat(everyEventOn(screen)).isEmpty();
            assertThat(operator.timesAsked).isEqualTo(1);
        }

        @Test
        @DisplayName("clearing the flag stops the asking")
        void clearingTheFlagStopsTheAsking() {
            AnOperatorAskedToConfirm operator = new AnOperatorAskedToConfirm(false);
            DesktopScreen screen = screenAsking(operator);
            screen.navigation().guardLeaving(true);
            screen.navigation().guardLeaving(false);

            screen.requestClose(window);

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type).containsExactly(ScreenEventType.CLOSE);
            assertThat(operator.timesAsked).isZero();
        }
    }

    @Nested
    @DisplayName("the operator's back and forward")
    class TheOperatorsBackAndForward {

        @Test
        @DisplayName("Alt+Left moves back and still reaches the app as the key")
        void altLeftMovesBackAndIsAKey() {
            DesktopScreen screen = screenAsking(new AnOperatorAskedToConfirm(true));
            screen.navigation().goTo(place("a"));

            screen.pressKey(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK, KeyEvent.CHAR_UNDEFINED, window);

            List<ScreenEvent> heard = everyEventOn(screen);
            assertThat(heard).extracting(ScreenEvent::type)
                    .containsExactlyInAnyOrder(ScreenEventType.CONTROL, ScreenEventType.BACK);
            assertThat(heard).filteredOn(each -> each.type() == ScreenEventType.BACK)
                    .extracting(ScreenEvent::detail)
                    .containsExactly(new ScreenEventDetail.Arrived(Place.start()));
        }

        @Test
        @DisplayName("Alt+Right after going back moves forward")
        void altRightMovesForward() {
            DesktopScreen screen = screenAsking(new AnOperatorAskedToConfirm(true));
            screen.navigation().goTo(place("a"));
            screen.pressKey(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK, KeyEvent.CHAR_UNDEFINED, window);
            everyEventOn(screen);

            screen.pressKey(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK, KeyEvent.CHAR_UNDEFINED, window);

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type)
                    .contains(ScreenEventType.FORWARD);
            assertThat(screen.navigation().current()).isEqualTo(place("a"));
        }

        @Test
        @DisplayName("at the first entry the chord is only the key")
        void atTheStartTheChordIsOnlyAKey() {
            DesktopScreen screen = screenAsking(new AnOperatorAskedToConfirm(true));
            screen.navigation().current();

            screen.pressKey(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK, KeyEvent.CHAR_UNDEFINED, window);

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type).containsExactly(ScreenEventType.CONTROL);
        }

        @Test
        @DisplayName("a plain Left is only the key")
        void aPlainArrowIsOnlyAKey() {
            DesktopScreen screen = screenAsking(new AnOperatorAskedToConfirm(true));
            screen.navigation().goTo(place("a"));

            screen.pressKey(KeyEvent.VK_LEFT, 0, KeyEvent.CHAR_UNDEFINED, window);

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type).containsExactly(ScreenEventType.CONTROL);
        }

        @Test
        @DisplayName("the mouse's back button moves back, and is still a press")
        void theBackButtonMovesBack() {
            DesktopScreen screen = screenAsking(new AnOperatorAskedToConfirm(true));
            screen.navigation().goTo(place("a"));

            screen.pressButton(4, 5, 6, window);

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type)
                    .containsExactlyInAnyOrder(ScreenEventType.DOWN, ScreenEventType.BACK);
        }

        @Test
        @DisplayName("an ordinary click is only a press")
        void anOrdinaryClickIsOnlyAPress() {
            DesktopScreen screen = screenAsking(new AnOperatorAskedToConfirm(true));
            screen.navigation().goTo(place("a"));

            screen.pressButton(1, 5, 6, window);

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type).containsExactly(ScreenEventType.DOWN);
        }

        @Test
        @DisplayName("the app's own back in a window arrives at once, as the operator's would")
        void theAppsBackArrivesAtOnce() {
            DesktopScreen screen = screenAsking(new AnOperatorAskedToConfirm(true));
            screen.navigation().goTo(place("a"));

            screen.navigation().goBack();

            assertThat(everyEventOn(screen)).extracting(ScreenEvent::type).containsExactly(ScreenEventType.BACK);
        }
    }
}
