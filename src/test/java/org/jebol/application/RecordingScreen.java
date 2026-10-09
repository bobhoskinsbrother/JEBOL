package org.jebol.application;

import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.*;

final class RecordingScreen extends GobScreen {

    private final boolean present;
    private final Map<ScreenMetric, PairValue> measurements =
            new EnumMap<>(ScreenMetric.class);
    private int displays = 1;
    private boolean operatorClosesWhateverOpens;

    private final List<GobValue> shown = new ArrayList<>();
    private final List<GobValue> opened = new ArrayList<>();
    private final List<GobValue> refreshed = new ArrayList<>();
    private final List<GobValue> closed = new ArrayList<>();
    private final List<GobValue> withWindows = new ArrayList<>();

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
    public void show(GobValue gob) {
        if (present) {
            shown.add(gob);
        }
        super.show(gob);
    }

    @Override
    protected Denied nothingToShowOn() {
        return new Denied("no-service", "this test screen has no display");
    }

    @Override
    protected List<GobValue> gobsWithWindows() {
        return List.copyOf(withWindows);
    }

    @Override
    protected void openTheWindowFor(GobValue gob) {
        withWindows.add(gob);
        opened.add(gob);
        if (operatorClosesWhateverOpens) {
            queued.add(ScreenEventKind.CLOSE, gob);
        }
    }

    @Override
    protected void repaintTheWindowFor(GobValue gob) {
        refreshed.add(gob);
    }

    @Override
    protected void closeTheWindowFor(GobValue gob) {
        if (withWindows.removeIf(gob::sharesStorageWith)) {
            closed.add(gob);
        }
    }

    void theOperatorDoes(ScreenEventKind kind, GobValue window) {
        queued.add(kind, window);
    }

    void theOperatorDoes(ScreenEventKind kind, GobValue window, ScreenEventDetail detail) {
        queued.add(kind, window, detail);
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
