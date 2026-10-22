package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventType;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Optional;

final class BackForwardChords {

    private static final int KEYBOARD_MODIFIERS = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK
            | InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;

    private static final Map<Integer, ScreenEventType> WITH_ALT = Map.of(
            KeyEvent.VK_LEFT, ScreenEventType.BACK,
            KeyEvent.VK_RIGHT, ScreenEventType.FORWARD);

    private static final Map<Integer, ScreenEventType> WITH_COMMAND = Map.of(
            KeyEvent.VK_OPEN_BRACKET, ScreenEventType.BACK,
            KeyEvent.VK_LEFT, ScreenEventType.BACK,
            KeyEvent.VK_CLOSE_BRACKET, ScreenEventType.FORWARD,
            KeyEvent.VK_RIGHT, ScreenEventType.FORWARD);

    private static final Map<Integer, ScreenEventType> SIDE_BUTTONS = Map.of(
            4, ScreenEventType.BACK,
            5, ScreenEventType.FORWARD);

    private final int chordModifier;
    private final Map<Integer, ScreenEventType> chordKeys;

    BackForwardChords(boolean onMac) {
        this.chordModifier = onMac ? InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
        this.chordKeys = onMac ? WITH_COMMAND : WITH_ALT;
    }

    Optional<ScreenEventType> keyDirection(int keyCode, int modifiers) {
        return (modifiers & KEYBOARD_MODIFIERS) == chordModifier
                ? Optional.ofNullable(chordKeys.get(keyCode))
                : Optional.empty();
    }

    Optional<ScreenEventType> buttonDirection(int button) {
        return Optional.ofNullable(SIDE_BUTTONS.get(button));
    }
}
