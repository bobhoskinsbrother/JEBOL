package org.jebol.domain.host;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.LongSupplier;

public final class History {

    public static final int MOST_CHANGES_PER_WINDOW = 100;

    public static final long CHANGE_WINDOW_MILLISECONDS = 30_000;

    private static final String RATE_EXCEEDED =
            MOST_CHANGES_PER_WINDOW + " changes to the history in " + CHANGE_WINDOW_MILLISECONDS / 1_000 + " seconds";

    private final LongSupplier millisecondsNow;
    private final TreeMap<Integer, Place> entries = new TreeMap<>();
    private final Deque<Long> changedAt = new ArrayDeque<>();
    private int position;
    private boolean usedByApp;
    private Optional<String> refusedAddress = Optional.empty();

    public History(LongSupplier millisecondsNow) {
        this.millisecondsNow = millisecondsNow;
        entries.put(0, Place.start());
    }

    public void startAt(int index, String address) {
        requireEntry(index);
        Optional<Place> read = Place.readAddress(address);
        entries.clear();
        entries.put(index, read.orElseGet(Place::start));
        position = index;
        refusedAddress = read.isPresent() ? Optional.empty() : Optional.of(address);
    }

    public Place current() {
        return entryAt(position);
    }

    public int position() {
        return position;
    }

    public Optional<String> refusedAddress() {
        return refusedAddress;
    }

    public boolean isUsedByApp() {
        return usedByApp;
    }

    public void markUsed() {
        usedByApp = true;
    }

    public Optional<HistoryEntry> goTo(Place place) {
        usedByApp = true;
        if (place.equals(current())) {
            return Optional.empty();
        }
        countChange();
        entries.tailMap(position, false).clear();
        position++;
        entries.put(position, place);
        return Optional.of(new HistoryEntry(position, place.address()));
    }

    public HistoryEntry replace(Place place) {
        usedByApp = true;
        countChange();
        entries.put(position, place);
        return new HistoryEntry(position, place.address());
    }

    public Optional<HistoryEntry> backTarget() {
        usedByApp = true;
        return operatorBackTarget().map(this::countAsChange);
    }

    public Optional<HistoryEntry> forwardTarget() {
        usedByApp = true;
        return operatorForwardTarget().map(this::countAsChange);
    }

    public Optional<HistoryEntry> operatorBackTarget() {
        return position == 0
                ? Optional.empty()
                : Optional.of(new HistoryEntry(position - 1, entryAt(position - 1).address()));
    }

    public Optional<HistoryEntry> operatorForwardTarget() {
        return entries.containsKey(position + 1)
                ? Optional.of(new HistoryEntry(position + 1, entryAt(position + 1).address()))
                : Optional.empty();
    }

    public Optional<ScreenEventType> arriveAt(int index, Place place) {
        requireEntry(index);
        Optional<ScreenEventType> direction = index == position
                ? Optional.empty()
                : Optional.of(index < position ? ScreenEventType.BACK : ScreenEventType.FORWARD);
        entries.put(index, place);
        position = index;
        return direction;
    }

    private Place entryAt(int index) {
        return entries.getOrDefault(index, Place.start());
    }

    private HistoryEntry countAsChange(HistoryEntry target) {
        countChange();
        return target;
    }

    private void countChange() {
        long now = millisecondsNow.getAsLong();
        while (!changedAt.isEmpty() && now - changedAt.peekFirst() >= CHANGE_WINDOW_MILLISECONDS) {
            changedAt.pollFirst();
        }
        if (changedAt.size() >= MOST_CHANGES_PER_WINDOW) {
            throw Raised.of(EvaluationFailure.SIZE_LIMIT, RATE_EXCEEDED);
        }
        changedAt.addLast(now);
    }

    private void requireEntry(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("a history has no entry " + index);
        }
    }
}
