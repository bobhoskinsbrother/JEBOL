package org.jebol.domain.eval;

import org.jebol.domain.value.GobValue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class ScreenEventQueue {

    private final Deque<ScreenEvent> queued = new ArrayDeque<>();

    public synchronized void add(ScreenEventKind kind, GobValue window) {
        queued.add(new ScreenEvent(kind, window));
    }

    public synchronized List<ScreenEvent> takeAll() {
        List<ScreenEvent> taken = List.copyOf(queued);
        queued.clear();
        return taken;
    }
}
