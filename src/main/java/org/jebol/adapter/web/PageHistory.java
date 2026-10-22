package org.jebol.adapter.web;

import org.jebol.domain.host.HistoryEntry;
import org.jebol.domain.host.Navigation;
import org.jebol.domain.host.NavigationPort;

final class PageHistory implements NavigationPort {

    private static final String PUSH = "push";
    private static final String REPLACE = "replace";
    private static final String BACK = "back";
    private static final String FORWARD = "forward";

    private final BrowserScreen.Viewer viewer;

    PageHistory(BrowserScreen.Viewer viewer) {
        this.viewer = viewer;
    }

    @Override
    public void push(HistoryEntry entry) {
        viewer.changeHistory(PUSH, entry);
    }

    @Override
    public void replace(HistoryEntry entry) {
        viewer.changeHistory(REPLACE, entry);
    }

    @Override
    public void back(HistoryEntry target, Navigation arrivals) {
        viewer.changeHistory(BACK, target);
    }

    @Override
    public void forward(HistoryEntry target, Navigation arrivals) {
        viewer.changeHistory(FORWARD, target);
    }

    @Override
    public void guardLeaving(boolean unsavedWork) {
        viewer.guardLeaving(unsavedWork);
    }
}
