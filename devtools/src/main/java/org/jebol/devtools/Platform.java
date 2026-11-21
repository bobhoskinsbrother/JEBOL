package org.jebol.devtools;

import java.util.Locale;

public enum Platform {
    WEB(Machine.ANY),
    ANDROID(Machine.ANY),
    LINUX(Machine.LINUX),
    MACOS(Machine.MAC),
    WINDOWS(Machine.WINDOWS);

    public enum Machine {
        ANY("any machine"),
        LINUX("Linux"),
        MAC("macOS"),
        WINDOWS("Windows");

        private final String named;

        Machine(String named) {
            this.named = named;
        }

        public String named() {
            return named;
        }

        public static Machine runningHere(String systemName) {
            String system = systemName.toLowerCase(Locale.ROOT);
            if (system.startsWith("windows")) {
                return WINDOWS;
            }
            if (system.startsWith("mac")) {
                return MAC;
            }
            return LINUX;
        }
    }

    private final Machine buildsOn;

    Platform(Machine buildsOn) {
        this.buildsOn = buildsOn;
    }

    public String folder() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Machine buildsOn() {
        return buildsOn;
    }

    public boolean canBuildOn(Machine machine) {
        return buildsOn == Machine.ANY || buildsOn == machine;
    }

    public static boolean isProjectFolder(String name) {
        for (Platform platform : values()) {
            if (platform.folder().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
