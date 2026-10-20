package org.jebol.application;

import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.render.TextExtent;
import org.jebol.domain.render.TextMeasurement;
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

    static RecordingScreen measure(int across, int down) {
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

    RecordingScreen closeWhateverOpens() {
        this.operatorClosesWhateverOpens = true;
        return this;
    }

    @Override
    public boolean hasDisplay() {
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
    public TextMeasurement textMeasurement() {
        if (!present) {
            return ScreenPort.none().textMeasurement();
        }
        return run -> new TextExtent(
                run.text().codePointCount(0, run.text().length()) * run.size() / 2,
                run.size() * 3 / 4,
                run.size() / 4);
    }

    @Override
    protected Denied refuseWithNoService() {
        return new Denied("no-service", "this test screen has no display");
    }

    @Override
    protected List<GobValue> gobWithWindow() {
        return List.copyOf(withWindows);
    }

    @Override
    protected void openWindowFor(GobValue gob) {
        withWindows.add(gob);
        opened.add(gob);
        if (operatorClosesWhateverOpens) {
            post(ScreenEventType.CLOSE, gob);
        }
    }

    @Override
    protected void repaintWindowFor(GobValue gob) {
        refreshed.add(gob);
    }

    @Override
    protected void closeWindowFor(GobValue gob) {
        if (withWindows.removeIf(gob::sharesStorageWith)) {
            closed.add(gob);
        }
    }

    void actAsTheOperator(ScreenEventType kind, GobValue window) {
        post(kind, window);
    }

    void actAsTheOperator(ScreenEventType kind, GobValue window, ScreenEventDetail detail) {
        post(kind, window, detail);
    }

    GobValue rootGob() {
        return root;
    }

    List<GobValue> listWhatWasShown() {
        return List.copyOf(shown);
    }

    List<GobValue> listWhatOpened() {
        return List.copyOf(opened);
    }

    List<GobValue> listWhatWasRefreshed() {
        return List.copyOf(refreshed);
    }

    List<GobValue> listWhatIsStandingOpen() {
        return List.copyOf(withWindows);
    }

    void reportFromAnotherThread(ScreenEventType kind, GobValue window)
            throws InterruptedException {
        Thread toolkit = new Thread(() -> actAsTheOperator(kind, window), "toolkit");
        toolkit.start();
        toolkit.join();
    }

    List<GobValue> listWhatClosed() {
        return List.copyOf(closed);
    }
}
