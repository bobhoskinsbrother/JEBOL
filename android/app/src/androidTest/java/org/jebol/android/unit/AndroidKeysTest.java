package org.jebol.android.unit;

import android.view.KeyEvent;

import org.jebol.adapter.android.AndroidKeys;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AndroidKeysTest {

    @ParameterizedTest(name = "key code {0} is the control {1}")
    @CsvSource({
            KeyEvent.KEYCODE_DPAD_LEFT + ", left",
            KeyEvent.KEYCODE_DPAD_RIGHT + ", right",
            KeyEvent.KEYCODE_DPAD_UP + ", up",
            KeyEvent.KEYCODE_DPAD_DOWN + ", down",
            KeyEvent.KEYCODE_MOVE_HOME + ", home",
            KeyEvent.KEYCODE_MOVE_END + ", end",
            KeyEvent.KEYCODE_PAGE_UP + ", page-up",
            KeyEvent.KEYCODE_PAGE_DOWN + ", page-down",
            KeyEvent.KEYCODE_INSERT + ", insert",
    })
    @DisplayName("a key that types nothing is a control event named as the desktop names it")
    void namesAControl(int keyCode, String name) {
        assertThat(AndroidKeys.reportFor(keyCode)).containsExactly(
                new AndroidKeys.Report(ScreenEventType.CONTROL, new ScreenEventDetail.NamedKey(name)));
    }

    @Test
    @DisplayName("delete is the key 8 and the keyboard's enter is the key 13")
    void deletesAndReturns() {
        assertThat(AndroidKeys.reportFor(KeyEvent.KEYCODE_DEL)).containsExactly(
                new AndroidKeys.Report(ScreenEventType.KEY, new ScreenEventDetail.Typed(8)));
        assertThat(AndroidKeys.reportFor(KeyEvent.KEYCODE_ENTER)).containsExactly(
                new AndroidKeys.Report(ScreenEventType.KEY, new ScreenEventDetail.Typed(13)));
    }

    @Test
    @DisplayName("a key Android has and the desktop does not name is no event at all")
    void ignoresWhatTheDesktopDoesNotName() {
        assertThat(AndroidKeys.reportFor(KeyEvent.KEYCODE_VOLUME_UP)).isEmpty();
    }

    @Test
    @DisplayName("committed text is one key event per code point, outside the basic plane too")
    void typesEachCodePoint() {
        assertThat(AndroidKeys.reportsForText("aé😀")).containsExactly(
                new AndroidKeys.Report(ScreenEventType.KEY, new ScreenEventDetail.Typed('a')),
                new AndroidKeys.Report(ScreenEventType.KEY, new ScreenEventDetail.Typed('é')),
                new AndroidKeys.Report(ScreenEventType.KEY, new ScreenEventDetail.Typed(0x1F600)));
        assertThat(AndroidKeys.reportsForText("")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("a newline in committed text is the key 13, as the action key is")
    void typesANewlineAsReturn() {
        assertThat(AndroidKeys.reportsForText("\n")).containsExactly(
                new AndroidKeys.Report(ScreenEventType.KEY, new ScreenEventDetail.Typed(13)));
    }
}
