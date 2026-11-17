package org.jebol.adapter.web;

import org.jebol.application.OneSurfaceScreen;
import org.jebol.domain.host.HistoryEntry;
import org.jebol.domain.host.ScreenCursor;

public interface PageViewer extends OneSurfaceScreen.Viewer {

    @Override
    default void useCursor(ScreenCursor cursor) {
        useCursor(new CursorMessage(cursor).write());
    }

    default void useCursor(String message) {
    }

    default void changeHistory(String move, HistoryEntry entry) {
    }

    default void guardLeaving(boolean unsavedWork) {
    }
}
