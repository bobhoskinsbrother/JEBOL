package org.jebol.application;

import org.jebol.domain.eval.ScreenEvent;
import org.jebol.domain.eval.ScreenEventKind;
import org.jebol.domain.eval.ScreenMetric;
import org.jebol.domain.eval.ScreenPort;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A screen a test can measure, and a record of what was drawn on it.
 *
 * <p>The gate runs with {@code java.awt.headless=true}, so the real adapter
 * refuses on every machine the suite ever runs on. That is deliberate and it
 * leaves this as the only way to drive the screen through REBOL and then
 * assert on what happened: a test says what the display measures, runs a
 * script, and reads back which windows opened and closed.
 *
 * <p>Setup reaches behind the interface -- a test pushes an event as though
 * the operator had closed a window -- and no test body does. What a script
 * cannot do, a test does not do either.
 */
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

    /** A display of the size given, with plausible furniture around it. */
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

    /** A machine with no display, which is what the build server is. */
    static RecordingScreen absent() {
        return new RecordingScreen(false);
    }

    RecordingScreen withDisplays(int howMany) {
        this.displays = howMany;
        return this;
    }

    /**
     * An operator who closes every window the moment it appears.
     *
     * <p>What a person does at the end of a session, done immediately, which
     * is the only way a test can drive a script that ends in VIEW: DO-EVENTS
     * returns when the last window closes, so without someone closing them it
     * never returns at all.
     */
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

    /**
     * What SHOW does when it is handed the root: close what left the pane,
     * then open what arrived in it.
     *
     * <p>Closing first, which is the order the C walks and is not arbitrary. A
     * host with a fixed number of window slots that opened first could run out
     * while still holding slots for windows already dismissed.
     */
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

    /** As though the operator had done something to a window. */
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

    /** The windows standing open right now, which is the projection to check. */
    List<GobValue> whatIsStandingOpen() {
        return List.copyOf(withWindows);
    }

    /** As though the toolkit's own thread had reported something. */
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
