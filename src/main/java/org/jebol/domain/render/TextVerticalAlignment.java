package org.jebol.domain.render;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum TextVerticalAlignment {
    TOP,
    MIDDLE,
    BOTTOM;

    public static Optional<TextVerticalAlignment> spelt(String canonical) {
        return Arrays.stream(values()).filter(each -> each.asJson().equals(canonical)).findFirst();
    }

    public String asJson() {
        return name().toLowerCase(Locale.ROOT);
    }
}
