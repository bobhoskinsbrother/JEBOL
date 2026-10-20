package org.jebol.application;

import org.jebol.domain.render.DefaultFontRenderer;
import org.jebol.domain.render.TextMeasurement;

import java.io.IOException;
import java.io.InputStream;

public final class DefaultFont {

    private static final String DEFAULT_FONT_PATH = "/org/jebol/fonts/NotoSans-Regular.ttf";
    private static final byte[] DEFAULT_FONT_BYTES = loadFont();
    private static final DefaultFontRenderer DEFAULT_FONT = DefaultFontRenderer.readFrom(DEFAULT_FONT_BYTES);

    private DefaultFont() {
    }

    public static TextMeasurement measurement() {
        return renderer().measure();
    }

    public static DefaultFontRenderer renderer() {
        return DEFAULT_FONT;
    }

    public static byte[] readFontBytes() {
        return DEFAULT_FONT_BYTES.clone();
    }

    private static byte[] loadFont() {
        try (InputStream reading = DefaultFont.class.getResourceAsStream(DEFAULT_FONT_PATH)) {
            if (reading == null) {
                throw new IllegalStateException("the font this build carries is missing: " + DEFAULT_FONT_PATH);
            }
            return reading.readAllBytes();
        } catch (IOException unreadable) {
            throw new IllegalStateException("the font this build carries would not read: " + DEFAULT_FONT_PATH, unreadable);
        }
    }
}
