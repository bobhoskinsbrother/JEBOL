package org.jebol.adapter.web;

import org.jebol.application.OneSurfaceScreen;
import org.jebol.domain.host.FilePort;

import java.util.function.LongSupplier;

public final class PageScreen {

    private PageScreen() {
    }

    public static OneSurfaceScreen showTo(PageViewer viewer, FilePort files) {
        return OneSurfaceScreen.showTo(viewer, new PageHistory(viewer), files);
    }

    public static OneSurfaceScreen showTo(PageViewer viewer, FilePort files, LongSupplier millisecondsNow) {
        return OneSurfaceScreen.showTo(viewer, new PageHistory(viewer), files, millisecondsNow);
    }
}
