package org.jebol.domain.host;

import java.util.Arrays;
import java.util.Optional;

public enum WindowArrangement {

    RESTORE("restore"),
    MINIMIZE("minimize"),
    MAXIMIZE("maximize"),
    ACTIVATE("activate");

    private final String spelling;

    WindowArrangement(String spelling) {
        this.spelling = spelling;
    }

    public String spelling() {
        return spelling;
    }

    public static Optional<WindowArrangement> findArrangementNamed(String word) {
        return Arrays.stream(values()).filter(each -> each.spelling.equals(word)).findFirst();
    }
}
