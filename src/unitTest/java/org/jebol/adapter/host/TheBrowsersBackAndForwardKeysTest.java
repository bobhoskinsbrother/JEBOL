package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

        private final BackForwardChords chords = new BackForwardChords(DesktopSystem.WINDOWS);

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = DesktopSystem.class, names = {"WINDOWS", "X11"})
        @DisplayName("Alt+Left is back and Alt+Right is forward")
        void altArrowsMove(DesktopSystem system) {
            BackForwardChords onThatSystem = new BackForwardChords(system);
            assertThat(onThatSystem.keyDirection(KeyEvent.VK_LEFT, ALT)).contains(ScreenEventType.BACK);
            assertThat(onThatSystem.keyDirection(KeyEvent.VK_RIGHT, ALT)).contains(ScreenEventType.FORWARD);
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

        private final BackForwardChords chords = new BackForwardChords(DesktopSystem.MACOS);

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
    @DisplayName("the mouse on Windows and macOS")
    class TheMouseOnWindowsAndMacOs {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = DesktopSystem.class, names = {"WINDOWS", "MACOS"})
        @DisplayName("button four is back and button five forward")
        void theSideButtonsMove(DesktopSystem system) {
            BackForwardChords chords = new BackForwardChords(system);
            assertThat(chords.buttonDirection(4)).contains(ScreenEventType.BACK);
            assertThat(chords.buttonDirection(5)).contains(ScreenEventType.FORWARD);
        }

        @ParameterizedTest(name = "button {0}")
        @ValueSource(ints = {0, 1, 2, 3, 6, 7, -1})
        @DisplayName("every other button is only a press")
        void otherButtonsArePresses(int button) {
            assertThat(new BackForwardChords(DesktopSystem.WINDOWS).buttonDirection(button)).isEmpty();
            assertThat(new BackForwardChords(DesktopSystem.MACOS).buttonDirection(button)).isEmpty();
        }

        @ParameterizedTest(name = "button {0}")
        @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
        @DisplayName("no button is a sideways turn, because a sideways turn arrives as a wheel")
        void noButtonIsASidewaysTurn(int button) {
            assertThat(new BackForwardChords(DesktopSystem.WINDOWS).isSidewaysTurn(button)).isFalse();
            assertThat(new BackForwardChords(DesktopSystem.MACOS).isSidewaysTurn(button)).isFalse();
        }
    }

    @Nested
    @DisplayName("the mouse on Linux, where Java numbers X's buttons six to nine as four to seven")
    class TheMouseOnLinux {

        private final BackForwardChords chords = new BackForwardChords(DesktopSystem.X11);

        @Test
        @DisplayName("button six is back and button seven forward")
        void theSideButtonsMove() {
            assertThat(chords.buttonDirection(6)).contains(ScreenEventType.BACK);
            assertThat(chords.buttonDirection(7)).contains(ScreenEventType.FORWARD);
        }

        @ParameterizedTest(name = "button {0}")
        @ValueSource(ints = {4, 5})
        @DisplayName("buttons four and five are a sideways turn, and move nothing")
        void fourAndFiveAreASidewaysTurn(int button) {
            assertThat(chords.isSidewaysTurn(button)).isTrue();
            assertThat(chords.buttonDirection(button)).isEmpty();
        }

        @ParameterizedTest(name = "button {0}")
        @ValueSource(ints = {0, 1, 2, 3, 8, -1})
        @DisplayName("every other button is only a press")
        void otherButtonsArePresses(int button) {
            assertThat(chords.buttonDirection(button)).isEmpty();
            assertThat(chords.isSidewaysTurn(button)).isFalse();
        }
    }
}
