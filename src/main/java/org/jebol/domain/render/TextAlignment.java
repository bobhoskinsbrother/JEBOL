package org.jebol.domain.render;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum TextAlignment {
    LEFT("left"),
    CENTRE("center"),
    RIGHT("right");

    private final String spelling;

    TextAlignment(String spelling) {
        this.spelling = spelling;
    }

    public static Optional<TextAlignment> spelt(String canonical) {
        return Arrays.stream(values()).filter(each -> each.spelling.equals(canonical)).findFirst();
    }

    public String asJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
