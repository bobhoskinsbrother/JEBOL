package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.assertj.core.api.Assertions.assertThat;

class TheBrowsersBackAndForwardKeysTest {

    private static final int ALT = InputEvent.ALT_DOWN_MASK;
    private static final int COMMAND = InputEvent.META_DOWN_MASK;
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int CONTROL = InputEvent.CTRL_DOWN_MASK;

    @Nested
    @DisplayName("on Windows and Linux")
    class OnWindowsAndLinux {

        private final BackForwardChords chords = new BackForwardChords(false);

        @Test
        @DisplayName("Alt+Left is back and Alt+Right is forward")
        void altArrowsMove() {
            assertThat(chords.keyDirection(KeyEvent.VK_LEFT, ALT)).contains(ScreenEventType.BACK);
            assertThat(chords.keyDirection(KeyEvent.VK_RIGHT, ALT)).contains(ScreenEventType.FORWARD);
        }

        @Test
        @DisplayName("an arrow alone, or with another modifier, or with a second one, is only a key")
        void otherChordsAreKeys() {
            assertThat(chords.keyDirection(KeyEvent.VK_LEFT, 0)).isEmpty();
            assertThat(chords.keyDirection(KeyEvent.VK_LEFT, CONTROL)).isEmpty();
            assertThat(chords.keyDirection(KeyEvent.VK_LEFT, ALT | SHIFT)).isEmpty();
            assertThat(chords.keyDirection(KeyEvent.VK_LEFT, COMMAND)).isEmpty();
        }

        @Test
        @DisplayName("the macOS chords mean nothing here")
        void theMacChordsAreKeys() {
            assertThat(chords.keyDirection(KeyEvent.VK_OPEN_BRACKET, COMMAND)).isEmpty();
            assertThat(chords.keyDirection(KeyEvent.VK_CLOSE_BRACKET, COMMAND)).isEmpty();
        }

        @Test
        @DisplayName("Alt with any other key is only a key")
        void altWithAnotherKey() {
            assertThat(chords.keyDirection(KeyEvent.VK_UP, ALT)).isEmpty();
            assertThat(chords.keyDirection(KeyEvent.VK_A, ALT)).isEmpty();
        }
    }

    @Nested
    @DisplayName("on macOS")
    class OnMacOs {

        private final BackForwardChords chords = new BackForwardChords(true);

        @Test
        @DisplayName("Cmd+[ and Cmd+Left are back, Cmd+] and Cmd+Right are forward")
        void commandChordsMove() {
            assertThat(chords.keyDirection(KeyEvent.VK_OPEN_BRACKET, COMMAND)).contains(ScreenEventType.BACK);
            assertThat(chords.keyDirection(KeyEvent.VK_LEFT, COMMAND)).contains(ScreenEventType.BACK);
            assertThat(chords.keyDirection(KeyEvent.VK_CLOSE_BRACKET, COMMAND)).contains(ScreenEventType.FORWARD);
            assertThat(chords.keyDirection(KeyEvent.VK_RIGHT, COMMAND)).contains(ScreenEventType.FORWARD);
        }

        @Test
        @DisplayName("Alt+Left moves a word on a Mac, so it is only a key")
        void altArrowsAreKeys() {
            assertThat(chords.keyDirection(KeyEvent.VK_LEFT, ALT)).isEmpty();
            assertThat(chords.keyDirection(KeyEvent.VK_RIGHT, ALT)).isEmpty();
        }

        @Test
        @DisplayName("a second modifier makes it some other key")
        void aSecondModifierIsAnotherKey() {
            assertThat(chords.keyDirection(KeyEvent.VK_LEFT, COMMAND | SHIFT)).isEmpty();
            assertThat(chords.keyDirection(KeyEvent.VK_OPEN_BRACKET, COMMAND | ALT)).isEmpty();
            assertThat(chords.keyDirection(KeyEvent.VK_OPEN_BRACKET, 0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("the mouse")
    class TheMouse {

        private final BackForwardChords chords = new BackForwardChords(false);

        @Test
        @DisplayName("button four is back and button five forward, on every system")
        void theSideButtonsMove() {
            assertThat(chords.buttonDirection(4)).contains(ScreenEventType.BACK);
            assertThat(chords.buttonDirection(5)).contains(ScreenEventType.FORWARD);
            assertThat(new BackForwardChords(true).buttonDirection(4)).contains(ScreenEventType.BACK);
        }

        @ParameterizedTest(name = "button {0}")
        @ValueSource(ints = {0, 1, 2, 3, 6, -1})
        @DisplayName("every other button is only a press")
        void otherButtonsArePresses(int button) {
            assertThat(chords.buttonDirection(button)).isEmpty();
        }
    }
}
