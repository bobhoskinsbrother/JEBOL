package org.jebol.application;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.domain.render.TextMeasurement;

public final class TheCarriedFont {

    private static final DefaultFont CARRIED = DefaultFont.readFrom(new ClasspathFiles());

    private TheCarriedFont() {
    }

    public static DefaultFont font() {
        return CARRIED;
    }

    public static TextMeasurement measurement() {
        return CARRIED.measurement();
    }
}
