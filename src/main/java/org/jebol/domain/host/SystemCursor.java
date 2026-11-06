package org.jebol.domain.host;

import java.util.Arrays;
import java.util.Optional;

public enum SystemCursor {
    ARROW(32512),
    I_SHAPE(32513),
    WAIT(32514),
    CROSS(32515),
    UP_ARROW(32516),
    SIZE_NWSE(32642),
    SIZE_NESW(32643),
    SIZE_WE(32644),
    SIZE_NS(32645),
    SIZE_ALL(32646),
    NO(32648),
    HAND(32649),
    APP_START(32650),
    HELP(32651);

    private final long id;

    SystemCursor(long id) {
        this.id = id;
    }

    public long id() {
        return id;
    }

    public static Optional<SystemCursor> findById(long id) {
        return Arrays.stream(values()).filter(cursor -> cursor.id == id).findFirst();
    }
}
