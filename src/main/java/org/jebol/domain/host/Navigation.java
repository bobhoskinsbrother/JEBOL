package org.jebol.domain.host;

import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class Navigation {

    private final NavigationPort port;
    private final History history;
    private final ScreenEventQueue events;
    private final Queue<Report> reports = new ConcurrentLinkedQueue<>();
    private volatile boolean guarding;

    public Navigation(NavigationPort port, History history, ScreenEventQueue events) {
        this.port = port;
        this.history = history;
        this.events = events;
    }

    public Place goTo(Place place) {
        takeReports();
        history.goTo(place).ifPresent(port::push);
        return history.current();
    }

    public Place replace(Place place) {
        takeReports();
        port.replace(history.replace(place));
        return history.current();
    }

    public void goBack() {
        takeReports();
        history.backTarget().ifPresent(target -> port.back(target, this));
        takeReports();
    }

    public void goForward() {
        takeReports();
        history.forwardTarget().ifPresent(target -> port.forward(target, this));
        takeReports();
    }

    public Place current() {
        takeReports();
        history.markUsed();
        return history.current();
    }

    public Optional<String> refusedAddress() {
        takeReports();
        history.markUsed();
        return history.refusedAddress();
    }

    public void guardLeaving(boolean unsavedWork) {
        guarding = unsavedWork;
        port.guardLeaving(unsavedWork);
    }

    public boolean isGuarding() {
        return guarding;
    }

    public void reportStart(int index, String address) {
        reports.add(new Report.Start(index, address));
    }

    public void reportArrival(int index, String address) {
        reports.add(new Report.Arrival(index, address));
    }

    public void reportMoveRequest(ScreenEventType direction) {
        reports.add(new Report.MoveRequest(direction));
    }

    public void takeReports() {
        for (Report next = reports.poll(); next != null; next = reports.poll()) {
            take(next);
        }
    }

    private void take(Report report) {
        switch (report) {
            case Report.Start(int index, String address) -> history.startAt(index, address);
            case Report.Arrival(int index, String address) ->
                    arrive(index, Place.readAddress(address).orElseGet(Place::start));
            case Report.MoveRequest(ScreenEventType direction) -> operatorTarget(direction)
                    .ifPresent(target -> arrive(target.index(),
                            Place.readAddress(target.address()).orElseGet(Place::start)));
        }
    }

    private Optional<HistoryEntry> operatorTarget(ScreenEventType direction) {
        return direction == ScreenEventType.BACK
                ? history.operatorBackTarget()
                : history.operatorForwardTarget();
    }

    private void arrive(int index, Place place) {
        history.arriveAt(index, place)
                .filter(_ -> history.isUsedByApp())
                .ifPresent(direction -> events.add(direction, null, new ScreenEventDetail.Arrived(place)));
    }

    private sealed interface Report {

        record Start(int index, String address) implements Report {
        }

        record Arrival(int index, String address) implements Report {
        }

        record MoveRequest(ScreenEventType direction) implements Report {
        }
    }
}
