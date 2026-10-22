package org.jebol.domain.host;

/**
 * What a window can tell a script about.
 *
 * <p>The spellings are {@code EventCatalogue}'s, which are the C's, and the
 * order there cannot change because {@code reb-evtypes.h} is generated from
 * it. These ten are what a window says; the catalogue's other forty belong
 * to ports, to the console and to devices.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public enum ScreenEventType {

    CLOSE("close"),
    RESIZE("resize"),
    OFFSET("offset"),
    KEY("key"),
    KEY_UP("key-up"),
    CONTROL("control"),
    CONTROL_UP("control-up"),
    DOWN("down"),
    UP("up"),
    MOVE("move"),
    SCROLL_LINE("scroll-line"),
    SCROLL_PAGE("scroll-page"),
    BACK("back"),
    FORWARD("forward");

    private final String spelling;

    ScreenEventType(String spelling) {
        this.spelling = spelling;
    }

    /** The word a script reads from {@code event/type}. */
    public String spelling() {
        return spelling;
    }
}
