package org.jebol.adapter.web;

import org.jebol.application.DefaultFont;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.host.WindowsOnOneSurface;
import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class BrowserScreen extends GobScreen {

    private final Viewer viewer;
    private final DefaultFont font;
    private final List<GobValue> gobValues = new ArrayList<>();
    private final WindowsOnOneSurface surface = new WindowsOnOneSurface();
    private final Queue<ScreenPost> posts = new ConcurrentLinkedQueue<>();
    private final Queue<ScreenMeasurement> measurements = new ConcurrentLinkedQueue<>();
    private PairValue viewport = PairValue.of(0, 0);
    private boolean isPictureRequired;

    private BrowserScreen(Viewer viewer, DefaultFont font) {
        this.viewer = viewer;
        this.font = font;
    }

    public static BrowserScreen showTo(Viewer viewer, FilePort files) {
        return new BrowserScreen(viewer, DefaultFont.readFrom(files));
    }

    @Override
    public boolean hasDisplay() {
        return viewer.isConnected();
    }

    @Override
    public int displayCount() {
        return hasDisplay() ? 1 : 0;
    }

    public PairValue viewport() {
        return viewport;
    }

    public void measure(int width, int height) {
        if (width < 0 || height < 0) {
            return;
        }
        measurements.add(new ScreenMeasurement(width, height));
    }

    @Override
    public PairValue measure(ScreenMetric metric, int display) {
        if (!hasDisplay()) {
            return PairValue.of(0, 0);
        }
        return switch (metric) {
            case SCREEN_SIZE, WORK_SIZE -> viewport;
            case LOG_SIZE, PHYS_SIZE -> PairValue.of(1, 1);
            case SCREEN_DPI -> PairValue.of(96, 96);
            case SCREEN_ORIGIN, WORK_ORIGIN, TITLE_SIZE, BORDER_SIZE,
                    BORDER_FIXED, WINDOW_MIN_SIZE -> PairValue.of(0, 0);
            case SCREENS -> PairValue.of(displayCount(), displayCount());
        };
    }

    @Override
    public void acceptRootGob(GobValue gobValue) {
        super.acceptRootGob(gobValue);
        if (!viewport.equals(PairValue.of(0, 0))) {
            gobValue.storage().size(viewport);
        }
    }

    @Override
    public void show(GobValue gob) {
        super.show(gob);
        if (root != null) {
            isPictureRequired = true;
        }
    }

    @Override
    public void offerShown() {
        if (!isPictureRequired || root == null) {
            return;
        }
        isPictureRequired = false;
        viewer.paint(paintPage());
    }


    @Override
    public TextMeasurement textMeasurement() {
        return hasDisplay() ? font.measurement() : ScreenPort.none().textMeasurement();
    }

    public void report(ScreenEventType kind, ScreenEventDetail detail) {
        posts.add(new ScreenPost(kind, detail));
    }

    @Override
    public void acceptPendingReports() {
        viewer.takeWhatThePagePosts();
        acceptMeasurements();
    }

    @Override
    public Optional<ScreenEvent> acceptNextEvent() {
        acceptPendingReports();
        addressAllEvents();
        Optional<ScreenEvent> next = super.acceptNextEvent();
        if (next.isEmpty()) {
            offerShown();
        }
        return next;
    }

    @Override
    protected Denied refuseWithNoService() {
        return new Denied("no-service", "no browser is attached to this screen");
    }

    @Override
    protected List<GobValue> gobWithWindow() {
        return List.copyOf(gobValues);
    }

    @Override
    protected void openWindowFor(GobValue gob) {
        gobValues.add(gob);
    }

    @Override
    protected void repaintWindowFor(GobValue gob) {
    }

    @Override
    protected void closeWindowFor(GobValue gob) {
        gobValues.removeIf(gob::sharesStorageWith);
    }

    private List<DrawingCommand> paintPage() {
        return PaintList.ofScreen(root,
                (int) Math.round(viewport.x()), (int) Math.round(viewport.y()),
                drawDialect).calls(textMeasurement());
    }

    private void acceptMeasurements() {
        for (ScreenMeasurement next = measurements.poll(); next != null; next = measurements.poll()) {
            dispatchToInterpreter(next.width(), next.height());
        }
    }

    private void addressAllEvents() {
        for (ScreenPost next = posts.poll(); next != null; next = posts.poll()) {
            List<ScreenEvent> events = surface.address(next.type(), next.detail(), listWindowsFromBottomToTop());
            for (ScreenEvent event : events) {
                queued.add(event.type(), event.window(), event.detail());
            }
        }
    }

    private void dispatchToInterpreter(int wide, int high) {
        this.viewport = PairValue.of(wide, high);
        if (root != null) {
            root.storage().size(viewport);
        }
    }

    private List<GobValue> listWindowsFromBottomToTop() {
        if (root == null) {
            return List.of();
        }
        return listTheChildrenOfTheRoot().stream().filter(this::hasWindow).toList();
    }


    public interface Viewer {
        boolean isConnected();
        void paint(List<DrawingCommand> painting);
        default void takeWhatThePagePosts() {
        }
    }

    private record ScreenPost(ScreenEventType type, ScreenEventDetail detail) {
    }

    private record ScreenMeasurement(int width, int height) {
    }

}
