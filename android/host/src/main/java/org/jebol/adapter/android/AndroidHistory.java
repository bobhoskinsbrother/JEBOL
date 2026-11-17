package org.jebol.adapter.android;

import org.jebol.domain.host.HistoryEntry;
import org.jebol.domain.host.Navigation;
import org.jebol.domain.host.NavigationPort;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class AndroidHistory implements NavigationPort {

    private final Consumer<Boolean> backWanted;
    private final TreeMap<Integer, String> addresses = new TreeMap<>(Map.of(0, ""));
    private int current;
    private boolean guarding;
    private boolean wasBackWanted;

    public AndroidHistory(Consumer<Boolean> backWanted) {
        this.backWanted = backWanted;
    }

    @Override
    public synchronized void push(HistoryEntry entry) {
        addresses.tailMap(entry.index(), true).clear();
        arriveAt(entry);
    }

    @Override
    public synchronized void replace(HistoryEntry entry) {
        arriveAt(entry);
    }

    @Override
    public void back(HistoryEntry target, Navigation arrivals) {
        synchronized (this) {
            arriveAt(target);
        }
        arrivals.reportArrival(target.index(), target.address());
    }

    @Override
    public void forward(HistoryEntry target, Navigation arrivals) {
        synchronized (this) {
            arriveAt(target);
        }
        arrivals.reportArrival(target.index(), target.address());
    }

    @Override
    public synchronized void guardLeaving(boolean unsavedWork) {
        guarding = unsavedWork;
        announceBackWanted();
    }

    public synchronized void followOperatorBack() {
        if (current > 0) {
            current--;
        }
        announceBackWanted();
    }

    public synchronized boolean hasAPlaceBehind() {
        return current > 0;
    }

    public synchronized boolean isGuarding() {
        return guarding;
    }

    public synchronized boolean isBackWanted() {
        return hasAPlaceBehind() || guarding;
    }

    public synchronized String currentAddress() {
        return addresses.getOrDefault(current, "");
    }

    public synchronized int currentIndex() {
        return current;
    }

    private void arriveAt(HistoryEntry entry) {
        addresses.put(entry.index(), entry.address());
        current = entry.index();
        announceBackWanted();
    }

    private void announceBackWanted() {
        boolean wanted = isBackWanted();
        if (wanted != wasBackWanted) {
            wasBackWanted = wanted;
            backWanted.accept(wanted);
        }
    }
}
