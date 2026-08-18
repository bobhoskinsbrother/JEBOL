package org.jebol.domain.eval;

/**
 * What a window can tell a script about.
 *
 * <p>The spellings are {@code EventCatalogue}'s, which are the C's, and the
 * order there cannot change because {@code reb-evtypes.h} is generated from
 * it. These eight are what a window says; the catalogue's other forty belong
 * to ports, to the console and to devices.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public enum ScreenEventKind {

    CLOSE("close"),
    RESIZE("resize"),
    OFFSET("offset"),
    KEY("key"),
    KEY_UP("key-up"),
    DOWN("down"),
    UP("up"),
    MOVE("move");

    private final String spelling;

    ScreenEventKind(String spelling) {
        this.spelling = spelling;
    }

    /** The word a script reads from {@code event/type}. */
    public String spelling() {
        return spelling;
    }
}
