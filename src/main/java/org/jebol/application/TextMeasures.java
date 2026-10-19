package org.jebol.application;

import org.jebol.domain.render.CarriedFont;
import org.jebol.domain.render.TextMeasure;

import java.io.IOException;
import java.io.InputStream;

public final class TextMeasures {

    private static final String THE_FONT = "/org/jebol/fonts/NotoSans-Regular.ttf";

    private TextMeasures() {
    }

    public static TextMeasure ofTheCarriedFont() {
        return loadTheCarriedFont().measure();
    }

    public static CarriedFont loadTheCarriedFont() {
        return THE_FONT_READ_ONCE;
    }

    public static byte[] readTheCarriedFontBytes() {
        return THE_FONT_BYTES.clone();
    }

    private static final byte[] THE_FONT_BYTES = readTheFont();

    private static final CarriedFont THE_FONT_READ_ONCE = CarriedFont.readFrom(THE_FONT_BYTES);

    private static byte[] readTheFont() {
        try (InputStream reading = TextMeasures.class.getResourceAsStream(THE_FONT)) {
            if (reading == null) {
                throw new IllegalStateException(
                        "the font this build carries is missing: " + THE_FONT);
            }
            return reading.readAllBytes();
        } catch (IOException unreadable) {
            throw new IllegalStateException(
                    "the font this build carries would not read: " + THE_FONT, unreadable);
        }
    }
}
