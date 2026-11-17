package org.jebol.adapter.android;

import android.view.KeyEvent;

import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AndroidKeys {

    private static final int BACKSPACE = 8;
    private static final int RETURN = 13;

    private static final Map<Integer, String> KEYS_THAT_TYPE_NOTHING = Map.ofEntries(
            Map.entry(KeyEvent.KEYCODE_PAGE_UP, "page-up"),
            Map.entry(KeyEvent.KEYCODE_PAGE_DOWN, "page-down"),
            Map.entry(KeyEvent.KEYCODE_MOVE_END, "end"),
            Map.entry(KeyEvent.KEYCODE_MOVE_HOME, "home"),
            Map.entry(KeyEvent.KEYCODE_DPAD_LEFT, "left"),
            Map.entry(KeyEvent.KEYCODE_DPAD_UP, "up"),
            Map.entry(KeyEvent.KEYCODE_DPAD_RIGHT, "right"),
            Map.entry(KeyEvent.KEYCODE_DPAD_DOWN, "down"),
            Map.entry(KeyEvent.KEYCODE_INSERT, "insert"));

    private static final Map<Integer, Integer> KEYS_THAT_TYPE_A_CONTROL_CHARACTER = Map.of(
            KeyEvent.KEYCODE_DEL, BACKSPACE,
            KeyEvent.KEYCODE_ENTER, RETURN,
            KeyEvent.KEYCODE_NUMPAD_ENTER, RETURN);

    private AndroidKeys() {
    }

    public static List<Report> reportFor(int keyCode) {
        Optional<String> named = Optional.ofNullable(KEYS_THAT_TYPE_NOTHING.get(keyCode));
        if (named.isPresent()) {
            return List.of(new Report(ScreenEventType.CONTROL, new ScreenEventDetail.NamedKey(named.get())));
        }
        Integer typed = KEYS_THAT_TYPE_A_CONTROL_CHARACTER.get(keyCode);
        return typed == null ? List.of() : List.of(typed(typed));
    }

    public static List<Report> reportsForText(CharSequence text) {
        return text.codePoints()
                .map(codePoint -> codePoint == '\n' ? RETURN : codePoint)
                .mapToObj(AndroidKeys::typed)
                .toList();
    }

    public static Report typed(int codePoint) {
        return new Report(ScreenEventType.KEY, new ScreenEventDetail.Typed(codePoint));
    }

    public record Report(ScreenEventType type, ScreenEventDetail detail) {
    }
}
