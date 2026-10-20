package org.jebol.application;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.render.DefaultFontRenderer;
import org.jebol.domain.render.TextMeasurement;

/**
 * The one font this build carries, read through the port a host supplies.
 *
 * <p>Read at the edge and handed onward as bytes: the metrics a screen answers
 * with and the face a toolkit draws with are the same font, so a caret worked
 * out by the domain sits where every adapter draws it. The read is the host's,
 * not this class's, because a build that reached for its own resource would be
 * an adapter doing input it does not own.
 */
public final class DefaultFont {

    private static final String WHERE_THE_CARRIED_FONT_IS =
            "/org/jebol/fonts/NotoSans-Regular.ttf";

    private final byte[] bytes;
    private final DefaultFontRenderer renderer;

    private DefaultFont(byte[] bytes) {
        this.bytes = bytes;
        this.renderer = DefaultFontRenderer.readFrom(bytes);
    }

    /**
     * The font, as the port reads it. A build whose font is missing fails
     * here, when the interpreter is built, rather than at the first
     * measurement.
     */
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
