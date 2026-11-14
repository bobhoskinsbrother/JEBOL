package org.jebol.adapter.web;

import org.jebol.application.DefaultFont;
import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.HistoryEntry;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.host.WindowArrangement;
import org.jebol.domain.host.WindowsOnOneSurface;
import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.value.GobStorage;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongSupplier;

public final class BrowserScreen extends GobScreen {

    private static final long NANOSECONDS_IN_A_MILLISECOND = 1_000_000;

    private final Viewer viewer;
    private final DefaultFont font;
    private final List<GobValue> gobValues = new ArrayList<>();
    private final WindowsOnOneSurface surface = new WindowsOnOneSurface();
    private final Queue<ScreenPost> posts = new ConcurrentLinkedQueue<>();
    private final Queue<ScreenMeasurement> measurements = new ConcurrentLinkedQueue<>();
    private final Map<GobStorage, PlaceBeforeMaximising> maximised = new IdentityHashMap<>();
    private final Set<GobStorage> minimised = Collections.newSetFromMap(new IdentityHashMap<>());
    private PairValue viewport = PairValue.of(0, 0);
    private boolean isPictureRequired;
    private Optional<String> titleShown = Optional.empty();

    private BrowserScreen(Viewer viewer, DefaultFont font, LongSupplier millisecondsNow) {
        super(millisecondsNow, new PageHistory(viewer));
        this.viewer = viewer;
        this.font = font;
    }

    public static BrowserScreen showTo(Viewer viewer, FilePort files) {
        return showTo(viewer, files, () -> System.nanoTime() / NANOSECONDS_IN_A_MILLISECOND);
    }

    public static BrowserScreen showTo(Viewer viewer, FilePort files, LongSupplier millisecondsNow) {
        return new BrowserScreen(viewer, DefaultFont.readFrom(files), millisecondsNow);
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
        showTitleIfChanged();
    }

    private void showTitleIfChanged() {
        Optional<String> title = gobValues.stream().findFirst().flatMap(this::readTitle);
        if (title.isPresent() && !title.equals(titleShown)) {
            titleShown = title;
            viewer.showTitle(title.get());
        }
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
        viewer.takePostedLines();
        acceptMeasurements();
        super.acceptPendingReports();
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
    protected Optional<ImagePort.Pixels> paintOffscreen(PaintList painting, int wide, int high) {
        return viewer.paintOffscreen(painting.calls(font.measurement()), wide, high);
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
        maximised.remove(gob.storage());
        minimised.remove(gob.storage());
    }

    @Override
    protected void arrangeOpenWindow(GobValue window, WindowArrangement arrangement) {
        switch (arrangement) {
            case MAXIMIZE -> maximise(window);
            case MINIMIZE -> minimise(window);
            case RESTORE -> restore(window);
            case ACTIVATE -> activate(window);
        }
        isPictureRequired = true;
    }

    private void maximise(GobValue window) {
        GobStorage held = window.storage();
        if (maximised.containsKey(held) && !minimised.contains(held)) {
            return;
        }
        minimised.remove(held);
        maximised.putIfAbsent(held, new PlaceBeforeMaximising(held.size(), held.offset()));
        fitToViewport(window);
        queued.add(ScreenEventType.MAXIMIZE, window, new ScreenEventDetail.NothingMore());
    }

    private void fitToViewport(GobValue window) {
        window.storage().setOffset(PairValue.of(0, 0));
        window.storage().size(viewport);
        queued.add(ScreenEventType.RESIZE, window,
                new ScreenEventDetail.At((int) Math.round(viewport.x()), (int) Math.round(viewport.y())));
    }

    private void minimise(GobValue window) {
        if (minimised.add(window.storage())) {
            queued.add(ScreenEventType.MINIMIZE, window, new ScreenEventDetail.NothingMore());
        }
    }

    private void restore(GobValue window) {
        GobStorage held = window.storage();
        boolean wasMinimised = minimised.remove(held);
        PlaceBeforeMaximising before = maximised.remove(held);
        if (!wasMinimised && before == null) {
            return;
        }
        if (before != null) {
            held.size(before.size());
            held.setOffset(before.offset());
        }
        queued.add(ScreenEventType.RESIZE, window, new ScreenEventDetail.At(
                (int) Math.round(held.size().x()), (int) Math.round(held.size().y())));
        queued.add(ScreenEventType.RESTORE, window, new ScreenEventDetail.NothingMore());
    }

    private void activate(GobValue window) {
        gobValues.removeIf(window::sharesStorageWith);
        gobValues.add(window);
        surface.giveKeyboardTo(window);
    }

    private List<DrawingCommand> paintPage() {
        return PaintList.ofScreen(root, listWhatIsPaintedFromBottomToTop(),
                (int) Math.round(viewport.x()), (int) Math.round(viewport.y()),
                drawDialect).calls(textMeasurement());
    }

    private List<GobValue> listWhatIsPaintedFromBottomToTop() {
        List<GobValue> painted = new ArrayList<>(rootPaneChildren().stream()
                .filter(child -> !hasWindow(child))
                .toList());
        painted.addAll(listWindowsFromBottomToTop());
        return painted;
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
        refitMaximisedWindows();
    }

    private void refitMaximisedWindows() {
        for (GobValue window : List.copyOf(gobValues)) {
            if (maximised.containsKey(window.storage()) && !window.storage().size().equals(viewport)) {
                fitToViewport(window);
                isPictureRequired = true;
            }
        }
    }

    private List<GobValue> listWindowsFromBottomToTop() {
        if (root == null) {
            return List.of();
        }
        return gobValues.stream()
                .filter(this::isInRootsPane)
                .filter(window -> !minimised.contains(window.storage()))
                .toList();
    }

    private record PlaceBeforeMaximising(PairValue size, PairValue offset) {
    }


    @Override
    public void takeTyping(String heading) {
        viewer.takeTyping(heading);
    }

    @Override
    public void releaseTyping() {
        viewer.releaseTyping();
    }

    @Override
    public void useCursor(ScreenCursor cursor) {
        viewer.useCursor(new CursorMessage(cursor).write());
    }

    public interface Viewer {
        boolean isConnected();
        void paint(List<DrawingCommand> painting);
        default void takePostedLines() {
        }
        default void takeTyping(String heading) {
        }
        default void releaseTyping() {
        }
        default void useCursor(String message) {
        }
        default void changeHistory(String move, HistoryEntry entry) {
        }
        default void guardLeaving(boolean unsavedWork) {
        }
        default void showTitle(String title) {
        }
        default Optional<ImagePort.Pixels> paintOffscreen(List<DrawingCommand> painting, int wide, int high) {
            return Optional.empty();
        }
    }

    private record ScreenPost(ScreenEventType type, ScreenEventDetail detail) {
    }

    private record ScreenMeasurement(int width, int height) {
    }

}
