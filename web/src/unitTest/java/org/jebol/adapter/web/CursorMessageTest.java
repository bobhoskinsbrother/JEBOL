package org.jebol.adapter.web;

import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.host.SystemCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CursorMessageTest {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "ARROW, default", "I_SHAPE, text", "WAIT, wait", "CROSS, crosshair", "UP_ARROW, n-resize",
            "SIZE_NWSE, nwse-resize", "SIZE_NESW, nesw-resize", "SIZE_WE, ew-resize", "SIZE_NS, ns-resize",
            "SIZE_ALL, move", "NO, not-allowed", "HAND, pointer", "APP_START, progress", "HELP, help"})
    @DisplayName("each system cursor names the CSS cursor of the same meaning")
    void namesTheCssCursor(SystemCursor system, String css) {
        assertThat(new CursorMessage(new ScreenCursor.FromSystem(system)).write())
                .isEqualTo("""
                        {"type":"cursor","css":"%s"}""".formatted(css));
    }

    @Test
    @DisplayName("a hidden pointer is the CSS cursor none")
    void hidesWithNone() {
        assertThat(new CursorMessage(new ScreenCursor.Hidden()).write())
                .isEqualTo("""
                        {"type":"cursor","css":"none"}""");
    }

    @Test
    @DisplayName("a picture carries its size and its pixels in base 64")
    void carriesThePixelsOfAPicture() {
        ImagePort.Pixels pixels = new ImagePort.Pixels(1, 1, new byte[] {1, 2, 3, 4});
        assertThat(new CursorMessage(new ScreenCursor.Picture(pixels)).write())
                .isEqualTo("""
                        {"type":"cursor","wide":1,"high":1,"rgba":"AQIDBA=="}""");
    }
}
