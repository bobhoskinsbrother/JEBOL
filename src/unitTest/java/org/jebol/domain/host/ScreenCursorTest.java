package org.jebol.domain.host;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenCursorTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(SystemCursor.class)
    @DisplayName("every system cursor is found by its own id")
    void findsEachSystemCursorByItsId(SystemCursor cursor) {
        assertThat(ScreenCursor.numbered(cursor.id())).isEqualTo(new ScreenCursor.FromSystem(cursor));
    }

    @Test
    @DisplayName("the ids are the Windows IDC values the 2010 GUI names")
    void usesTheIdsTheGuiNames() {
        assertThat(SystemCursor.ARROW.id()).isEqualTo(32512);
        assertThat(SystemCursor.NO.id()).isEqualTo(32648);
        assertThat(SystemCursor.HELP.id()).isEqualTo(32651);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(longs = {0, -1, 99, 32511, 32517, 32647, 32652, Long.MAX_VALUE})
    @DisplayName("an id either side of the known ones hides the pointer, as LoadCursor answering NULL does")
    void hidesThePointerForAnUnknownId(long id) {
        assertThat(ScreenCursor.numbered(id)).isEqualTo(new ScreenCursor.Hidden());
    }

    @Test
    @DisplayName("a picture keeps its pixels")
    void keepsThePixelsOfAPicture() {
        ImagePort.Pixels pixels = new ImagePort.Pixels(1, 1, new byte[] {1, 2, 3, 4});
        assertThat(ScreenCursor.painted(pixels)).isEqualTo(new ScreenCursor.Picture(pixels));
    }

    @ParameterizedTest(name = "{0}x{1}")
    @CsvSource({"0, 0", "0, 1", "1, 0"})
    @DisplayName("and a picture with nothing in it hides the pointer")
    void hidesThePointerForAnEmptyPicture(int wide, int high) {
        assertThat(ScreenCursor.painted(new ImagePort.Pixels(wide, high, new byte[0])))
                .isEqualTo(new ScreenCursor.Hidden());
    }
}
