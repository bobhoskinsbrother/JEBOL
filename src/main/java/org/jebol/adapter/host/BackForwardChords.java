package org.jebol.adapter.host;

import org.jebol.domain.host.ScreenEventType;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    private static final Map<Integer, ScreenEventType> SIDE_BUTTONS_ON_X11 = Map.of(
            6, ScreenEventType.BACK,
            7, ScreenEventType.FORWARD);

    private static final Set<Integer> SIDEWAYS_TURN_BUTTONS_ON_X11 = Set.of(4, 5);

    private final int chordModifier;
    private final Map<Integer, ScreenEventType> chordKeys;
    private final Map<Integer, ScreenEventType> sideButtons;
    private final Set<Integer> sidewaysTurnButtons;

    BackForwardChords(DesktopSystem system) {
        boolean onMac = system == DesktopSystem.MACOS;
        boolean onX11 = system == DesktopSystem.X11;
        this.chordModifier = onMac ? InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
        this.chordKeys = onMac ? WITH_COMMAND : WITH_ALT;
        this.sideButtons = onX11 ? SIDE_BUTTONS_ON_X11 : SIDE_BUTTONS;
        this.sidewaysTurnButtons = onX11 ? SIDEWAYS_TURN_BUTTONS_ON_X11 : Set.of();
    }

    Optional<ScreenEventType> keyDirection(int keyCode, int modifiers) {
        return (modifiers & KEYBOARD_MODIFIERS) == chordModifier
                ? Optional.ofNullable(chordKeys.get(keyCode))
                : Optional.empty();
    }

    Optional<ScreenEventType> buttonDirection(int button) {
        return Optional.ofNullable(sideButtons.get(button));
    }

    boolean isSidewaysTurn(int button) {
        return sidewaysTurnButtons.contains(button);
    }
}
