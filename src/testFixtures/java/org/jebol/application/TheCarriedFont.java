package org.jebol.application;

import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.adapter.host.Java2DSurface;
import org.jebol.domain.render.TextMeasurement;

import java.awt.Font;

public final class TheCarriedFont {

    private static final DefaultFont CARRIED = DefaultFont.readFrom(new ClasspathFiles());
    private static final Font FACE = Java2DSurface.faceFrom(CARRIED.readFontBytes());

    private TheCarriedFont() {
    }

    public static DefaultFont font() {
        return CARRIED;
    }

    public static TextMeasurement measurement() {
        return CARRIED.measurement();
    }

    public static Font face() {
        return FACE;
    }
}
