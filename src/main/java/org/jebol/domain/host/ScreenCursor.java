package org.jebol.domain.host;

public sealed interface ScreenCursor {

    record Hidden() implements ScreenCursor {
    }

    record FromSystem(SystemCursor cursor) implements ScreenCursor {
    }

    record Picture(ImagePort.Pixels pixels) implements ScreenCursor {
    }

    static ScreenCursor numbered(long id) {
        return SystemCursor.findById(id).<ScreenCursor>map(FromSystem::new).orElseGet(Hidden::new);
    }

    static ScreenCursor painted(ImagePort.Pixels pixels) {
        return pixels.wide() > 0 && pixels.high() > 0 ? new Picture(pixels) : new Hidden();
    }
}
