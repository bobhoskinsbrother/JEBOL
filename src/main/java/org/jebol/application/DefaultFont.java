package org.jebol.application;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.render.DefaultFontRenderer;
import org.jebol.domain.render.TextMeasurement;

public final class DefaultFont {

    private static final String WHERE_THE_CARRIED_FONT_IS =
            "/org/jebol/fonts/NotoSans-Regular.ttf";

    private final byte[] bytes;
    private final DefaultFontRenderer renderer;

    private DefaultFont(byte[] bytes) {
        this.bytes = bytes;
        this.renderer = DefaultFontRenderer.readFrom(bytes);
    }

    public static DefaultFont readFrom(FilePort files) {
        try {
            return new DefaultFont(files.readBytes(WHERE_THE_CARRIED_FONT_IS));
        } catch (FilePort.Denied missing) {
            throw new IllegalStateException(
                    WHERE_THE_CARRIED_FONT_IS + " is missing from the build", missing);
        }
    }

    public TextMeasurement measurement() {
        return renderer.measure();
    }

    public DefaultFontRenderer renderer() {
        return renderer;
    }

    public byte[] readFontBytes() {
        return bytes.clone();
    }
}
