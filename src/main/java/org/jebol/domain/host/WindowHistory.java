package org.jebol.domain.host;

public final class WindowHistory implements NavigationPort {

    @Override
    public void push(HistoryEntry entry) {
    }

    @Override
    public void replace(HistoryEntry entry) {
    }

    @Override
    public void back(HistoryEntry target, Navigation arrivals) {
        arrivals.reportArrival(target.index(), target.address());
    }

    @Override
    public void forward(HistoryEntry target, Navigation arrivals) {
        arrivals.reportArrival(target.index(), target.address());
    }

    @Override
    public void guardLeaving(boolean unsavedWork) {
    }
}
