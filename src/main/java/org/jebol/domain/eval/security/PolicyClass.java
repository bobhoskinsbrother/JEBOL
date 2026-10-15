package org.jebol.domain.eval.security;

import java.util.Locale;

public enum PolicyClass {
    FILE, NET, EVAL, MEMORY, SECURE, PROTECT, DEBUG, ENVR, CALL, BROWSE, EXTENSION;

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
