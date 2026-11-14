package org.jebol.adapter.host;

import java.util.Locale;

enum DesktopSystem {
    MACOS,
    WINDOWS,
    X11;

    private static final String MACOS_NAME_STARTS = "mac";
    private static final String WINDOWS_NAME_STARTS = "windows";

    static DesktopSystem named(String operatingSystemName) {
        String lowered = operatingSystemName.toLowerCase(Locale.ROOT);
        if (lowered.startsWith(MACOS_NAME_STARTS)) {
            return MACOS;
        }
        if (lowered.startsWith(WINDOWS_NAME_STARTS)) {
            return WINDOWS;
        }
        return X11;
    }
}
