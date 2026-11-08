package org.jebol.domain.host;

public record ConsoleKey(int character, int virtualKey, boolean control, boolean shift, boolean alt) {

    public static final int CTRL_C = 3;

    public boolean isCtrlC() {
        return character == CTRL_C;
    }
}
