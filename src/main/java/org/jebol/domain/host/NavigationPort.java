package org.jebol.domain.host;

public interface NavigationPort {

    void push(HistoryEntry entry);

    void replace(HistoryEntry entry);

    void back(HistoryEntry target, Navigation arrivals);

    void forward(HistoryEntry target, Navigation arrivals);

    void guardLeaving(boolean unsavedWork);
}
