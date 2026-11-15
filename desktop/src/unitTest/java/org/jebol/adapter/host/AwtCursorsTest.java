package org.jebol.adapter.host;

import org.jebol.domain.host.SystemCursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.awt.Cursor;

import static org.assertj.core.api.Assertions.assertThat;

class AwtCursorsTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(SystemCursor.class)
    @DisplayName("every system cursor has an AWT cursor, even where no display is there to load one")
    void answersEverySystemCursor(SystemCursor system) {
        assertThat(AwtCursors.predefinedFor(system)).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "ARROW, 0", "I_SHAPE, 2", "WAIT, 3", "CROSS, 1", "UP_ARROW, 8", "SIZE_NWSE, 6", "SIZE_NESW, 7",
            "SIZE_WE, 11", "SIZE_NS, 8", "SIZE_ALL, 13", "HAND, 12", "APP_START, 3", "HELP, 0"})
    @DisplayName("and it is the one AWT has of the same meaning")
    void answersTheCursorOfTheSameMeaning(SystemCursor system, int awtType) {
        assertThat(AwtCursors.predefinedFor(system)).isEqualTo(Cursor.getPredefinedCursor(awtType));
    }
}
