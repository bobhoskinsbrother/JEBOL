package org.jebol.application;

import org.jebol.domain.eval.ScreenEvent;
import org.jebol.domain.eval.ScreenEventKind;
import org.jebol.domain.eval.ScreenMetric;
import org.jebol.domain.eval.ScreenPort;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.*;

final class RecordingScreen implements ScreenPort {

    private final boolean present;
    private final Map<ScreenMetric, PairValue> measurements =
            new EnumMap<>(ScreenMetric.class);
    private int displays = 1;
    private boolean operatorClosesWhateverOpens;

    private GobValue root;
    private final List<GobValue> shown = new ArrayList<>();
    private final List<GobValue> opened = new ArrayList<>();
    private final List<GobValue> refreshed = new ArrayList<>();
    private final List<GobValue> closed = new ArrayList<>();
    private final List<GobValue> withWindows = new ArrayList<>();
    private final Deque<ScreenEvent> queued = new ArrayDeque<>();

    private RecordingScreen(boolean present) {
        this.present = present;
    }

    static RecordingScreen measuring(int across, int down) {
        RecordingScreen screen = new RecordingScreen(true);
        screen.measurements.put(ScreenMetric.SCREEN_SIZE, PairValue.of(across, down));
        screen.measurements.put(ScreenMetric.SCREEN_ORIGIN, PairValue.of(0, 0));
        screen.measurements.put(ScreenMetric.SCREEN_DPI, PairValue.of(96, 96));
        screen.measurements.put(ScreenMetric.WORK_ORIGIN, PairValue.of(0, 25));
        screen.measurements.put(ScreenMetric.WORK_SIZE, PairValue.of(across, down - 25));
        screen.measurements.put(ScreenMetric.TITLE_SIZE, PairValue.of(0, 22));
        screen.measurements.put(ScreenMetric.BORDER_SIZE, PairValue.of(4, 4));
        screen.measurements.put(ScreenMetric.BORDER_FIXED, PairValue.of(3, 3));
        screen.measurements.put(ScreenMetric.WINDOW_MIN_SIZE, PairValue.of(112, 27));
        screen.measurements.put(ScreenMetric.LOG_SIZE, PairValue.of(1, 1));
        screen.measurements.put(ScreenMetric.PHYS_SIZE, PairValue.of(1, 1));
        return screen;
    }

    static RecordingScreen absent() {
        return new RecordingScreen(false);
    }

    RecordingScreen withDisplays(int howMany) {
        this.displays = howMany;
        return this;
    }

    RecordingScreen whereTheOperatorClosesWhateverOpens() {
        this.operatorClosesWhateverOpens = true;
        return this;
    }

    @Override
    public boolean hasADisplay() {
        return present;
    }

    @Override
    public PairValue measure(ScreenMetric metric, int display) {
        if (!present) {
            return PairValue.of(0, 0);
        }
        return measurements.getOrDefault(metric, PairValue.of(0, 0));
    }

    @Override
    public int displayCount() {
        return present ? displays : 0;
    }

    @Override
    public void takeTheRootGob(GobValue given) {
        this.root = given;
    }

    @Override
    public void show(GobValue gob) {
        if (!present) {
            throw new Denied("no-service", "this test screen has no display");
        }
        shown.add(gob);
        if (gob == null || root == null) {
            return;
        }
        if (gob.sharesStorageWith(root)) {
            reconcileAgainstTheRoot();
            return;
        }
        if (isInTheRootsPane(gob)) {
            openOrRefresh(gob);
            return;
        }
        if (hasAWindow(gob)) {
            closeTheWindowFor(gob);
        }
    }

    private void reconcileAgainstTheRoot() {
        for (GobValue standing : List.copyOf(withWindows)) {
            if (!isInTheRootsPane(standing)) {
                closeTheWindowFor(standing);
            }
        }
        for (GobValue child : childrenOfTheRoot()) {
            openOrRefresh(child);
        }
    }

    private void openOrRefresh(GobValue gob) {
        if (hasAWindow(gob)) {
            refreshed.add(gob);
            return;
        }
        withWindows.add(gob);
        opened.add(gob);
        if (operatorClosesWhateverOpens) {
            queued.add(new ScreenEvent(ScreenEventKind.CLOSE, gob));
        }
    }

    private void closeTheWindowFor(GobValue gob) {
        withWindows.removeIf(gob::sharesStorageWith);
        closed.add(gob);
    }

    private boolean hasAWindow(GobValue gob) {
        return withWindows.stream().anyMatch(gob::sharesStorageWith);
    }

    private List<GobValue> childrenOfTheRoot() {
        List<GobValue> children = new ArrayList<>();
        for (var child : root.storage().pane()) {
            if (child instanceof GobValue gob) {
                children.add(gob);
            }
        }
        return List.copyOf(children);
    }

    private boolean isInTheRootsPane(GobValue gob) {
        return childrenOfTheRoot().stream().anyMatch(gob::sharesStorageWith);
    }

    @Override
    public synchronized List<ScreenEvent> takeQueuedEvents() {
        List<ScreenEvent> taken = List.copyOf(queued);
        queued.clear();
        return taken;
    }

    synchronized void theOperatorDoes(ScreenEventKind kind, GobValue window) {
        queued.add(new ScreenEvent(kind, window));
    }

    GobValue rootGob() {
        return root;
    }

    List<GobValue> whatWasShown() {
        return List.copyOf(shown);
    }

    List<GobValue> whatOpened() {
        return List.copyOf(opened);
    }

    List<GobValue> whatWasRefreshed() {
        return List.copyOf(refreshed);
    }

    List<GobValue> whatIsStandingOpen() {
        return List.copyOf(withWindows);
    }

    void reportFromAnotherThread(ScreenEventKind kind, GobValue window)
            throws InterruptedException {
        Thread toolkit = new Thread(() -> theOperatorDoes(kind, window), "toolkit");
        toolkit.start();
        toolkit.join();
    }

    List<GobValue> whatClosed() {
        return List.copyOf(closed);
    }
}
