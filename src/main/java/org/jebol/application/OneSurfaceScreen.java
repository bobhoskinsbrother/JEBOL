package org.jebol.application;

import org.jebol.domain.host.FilePort;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.NavigationPort;
import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.host.ShownWindow;
import org.jebol.domain.host.WindowArrangement;
import org.jebol.domain.host.WindowsOnOneSurface;
import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.host.ScreenEventType;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.render.Transform;
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

public final class OneSurfaceScreen extends GobScreen {

    private static final long NANOSECONDS_IN_A_MILLISECOND = 1_000_000;

    public static final int CSS_PIXELS_PER_INCH = 96;

    private final Viewer viewer;
    private final DefaultFont font;
    private final List<GobValue> gobValues = new ArrayList<>();
    private final WindowsOnOneSurface surface = new WindowsOnOneSurface();
    private final Queue<ScreenPost> posts = new ConcurrentLinkedQueue<>();
    private final Queue<ScreenMeasurement> measurements = new ConcurrentLinkedQueue<>();
    private final Map<GobStorage, PlaceBeforeMaximising> maximised = new IdentityHashMap<>();
    private final Set<GobStorage> minimised = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<GobStorage> minimisedByThePlatform = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Queue<SurfaceVisibility> visibilityChanges = new ConcurrentLinkedQueue<>();
    private final Queue<Integer> keyboardMeasurements = new ConcurrentLinkedQueue<>();
    private PairValue viewport = PairValue.of(0, 0);
    private boolean isPictureRequired;
    private Optional<String> titleShown = Optional.empty();
    private Optional<GobValue> keptAboveTheKeyboard = Optional.empty();
    private int keyboardHigh;

    private OneSurfaceScreen(Viewer viewer, NavigationPort navigation, DefaultFont font, LongSupplier millisecondsNow) {
        super(millisecondsNow, navigation);
        this.viewer = viewer;
        this.font = font;
    }

    public static OneSurfaceScreen showTo(Viewer viewer, NavigationPort navigation, FilePort files) {
        return showTo(viewer, navigation, files, () -> System.nanoTime() / NANOSECONDS_IN_A_MILLISECOND);
    }

    public static OneSurfaceScreen showTo(Viewer viewer, NavigationPort navigation, FilePort files,
                                          LongSupplier millisecondsNow) {
        return new OneSurfaceScreen(viewer, navigation, DefaultFont.readFrom(files), millisecondsNow);
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

    public void measureKeyboard(int high) {
        keyboardMeasurements.add(Math.max(0, high));
    }

    @Override
    public PairValue measure(ScreenMetric metric, int display) {
        if (!hasDisplay()) {
            return PairValue.of(0, 0);
        }
        return switch (metric) {
            case SCREEN_SIZE, WORK_SIZE -> viewport;
            case LOG_SIZE, PHYS_SIZE -> PairValue.of(1, 1);
            case SCREEN_DPI -> PairValue.of(viewer.screenDpi(), viewer.screenDpi());
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
            fitResizableWindows();
        }
    }

    private void fitResizableWindows() {
        if (viewport.x() <= 0 || viewport.y() <= 0) {
            return;
        }
        for (GobValue window : List.copyOf(gobValues)) {
            GobStorage held = window.storage();
            if (held.listRaisedFlags().contains(GobStorage.Flag.RESIZE) && !maximised.containsKey(held)) {
                fitInsideViewport(window);
            }
        }
    }

    private void fitInsideViewport(GobValue window) {
        GobStorage held = window.storage();
        boolean tooWide = held.size().x() > viewport.x();
        boolean tooHigh = held.size().y() > viewport.y();
        if (!tooWide && !tooHigh) {
            return;
        }
        double wide = Math.min(held.size().x(), viewport.x());
        double high = Math.min(held.size().y(), viewport.y());
        held.size(PairValue.of(wide, high));
        held.setOffset(PairValue.of(tooWide ? 0 : held.offset().x(), tooHigh ? 0 : held.offset().y()));
        queued.add(ScreenEventType.RESIZE, window,
                new ScreenEventDetail.At((int) Math.round(wide), (int) Math.round(high)));
        isPictureRequired = true;
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
        return hasDisplay() ? viewer.textMeasurement(font) : ScreenPort.none().textMeasurement();
    }

    public void report(ScreenEventType kind, ScreenEventDetail detail) {
        posts.add(new ScreenPost(kind, detail));
    }

    public void hideSurface() {
        visibilityChanges.add(SurfaceVisibility.HIDDEN);
    }

    public void showSurface() {
        visibilityChanges.add(SurfaceVisibility.SHOWN);
    }

    @Override
    public void acceptPendingReports() {
        viewer.takePostedLines();
        acceptMeasurements();
        acceptKeyboardMeasurements();
        acceptVisibilityChanges();
        super.acceptPendingReports();
    }

    private void acceptKeyboardMeasurements() {
        for (Integer next = keyboardMeasurements.poll(); next != null; next = keyboardMeasurements.poll()) {
            keyboardHigh = next;
            isPictureRequired = true;
        }
    }

    private void acceptVisibilityChanges() {
        for (SurfaceVisibility next = visibilityChanges.poll(); next != null; next = visibilityChanges.poll()) {
            switch (next) {
                case HIDDEN -> minimiseEveryWindow();
                case SHOWN -> restorePlatformMinimisedWindows();
            }
            isPictureRequired = true;
        }
    }

    private void minimiseEveryWindow() {
        for (GobValue window : List.copyOf(gobValues)) {
            if (minimised.add(window.storage())) {
                minimisedByThePlatform.add(window.storage());
                queued.add(ScreenEventType.MINIMIZE, window, new ScreenEventDetail.NothingMore());
            }
        }
    }

    private void restorePlatformMinimisedWindows() {
        for (GobValue window : List.copyOf(gobValues)) {
            if (minimisedByThePlatform.remove(window.storage())) {
                minimised.remove(window.storage());
                queued.add(ScreenEventType.RESTORE, window, new ScreenEventDetail.NothingMore());
            }
        }
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
        return viewer.paintOffscreen(painting.calls(viewer.textMeasurement(font)), wide, high);
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
        if (keptAboveTheKeyboard.map(attached -> windowHolding(attached).sharesStorageWith(gob)).orElse(false)) {
            lowerKeyboard();
        }
        gobValues.removeIf(gob::sharesStorageWith);
        maximised.remove(gob.storage());
        minimised.remove(gob.storage());
        minimisedByThePlatform.remove(gob.storage());
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
        List<GobValue> windows = listWindowsFromBottomToTop();
        List<GobValue> shownElsewhere = windows.stream().filter(this::isShownElsewhere).toList();
        List<GobValue> painted = new ArrayList<>(rootPaneChildren().stream()
                .filter(child -> !hasWindow(child))
                .toList());
        painted.addAll(windows.stream().filter(window -> !isShownElsewhere(window)).toList());
        List<DrawingCommand> page = new ArrayList<>(PaintList.ofScreen(root, painted,
                (int) Math.round(viewport.x()), (int) Math.round(viewport.y()),
                drawDialect).calls(textMeasurement()));
        int raisedBy = raisedForKeyboard();
        if (raisedBy > 0) {
            page.replaceAll(command -> DrawingCommand.placeUnder(command, Transform.moveBy(0, -raisedBy)));
        }
        for (GobValue window : shownElsewhere) {
            Transform outer = whereShown(window).asTransform();
            PaintList.ofWindow(window, drawDialect).calls(textMeasurement())
                    .forEach(command -> page.add(DrawingCommand.placeUnder(command, outer)));
        }
        return page;
    }

    private boolean isShownElsewhere(GobValue window) {
        ShownWindow shown = whereShown(window);
        return shown.scale() != 1.0
                || shown.across() != (int) Math.round(window.storage().offset().x())
                || shown.down() + raisedForKeyboard() != (int) Math.round(window.storage().offset().y());
    }

    private ShownWindow whereShown(GobValue window) {
        GobStorage held = window.storage();
        if (viewport.x() <= 0 || viewport.y() <= 0) {
            return ShownWindow.of(PairValue.of(Double.MAX_VALUE, Double.MAX_VALUE), held.size(), held.offset(), 0);
        }
        return ShownWindow.of(viewport, held.size(), held.offset(), raisedForKeyboard());
    }

    private int raisedForKeyboard() {
        if (keyboardHigh <= 0 || keptAboveTheKeyboard.isEmpty()) {
            return 0;
        }
        GobValue attached = keptAboveTheKeyboard.get();
        GobValue window = windowHolding(attached);
        if (gobValues.stream().noneMatch(window::sharesStorageWith)) {
            return 0;
        }
        GobStorage held = window.storage();
        ShownWindow shown = ShownWindow.of(viewport, held.size(), held.offset(), 0);
        double bottomInWindow = offsetWithinWindow(attached.storage(), held) + attached.storage().size().y();
        double bottomOnScreen = shown.down() + bottomInWindow * shown.scale();
        return (int) Math.max(0, Math.round(bottomOnScreen - (viewport.y() - keyboardHigh)));
    }

    private static double offsetWithinWindow(GobStorage gob, GobStorage window) {
        double down = 0;
        for (GobStorage at = gob; at != null && at != window; at = at.parent()) {
            down += at.offset().y();
        }
        return down;
    }

    private GobValue windowHolding(GobValue gob) {
        for (GobValue window : gobValues) {
            for (GobStorage at = gob.storage(); at != null; at = at.parent()) {
                if (at == window.storage()) {
                    return window;
                }
            }
        }
        return gob;
    }

    private boolean isOnAttachedGob(ScreenEventDetail detail) {
        int[] at = switch (detail) {
            case ScreenEventDetail.At(int across, int down) -> new int[] {across, down};
            case ScreenEventDetail.Touched(int across, int down) -> new int[] {across, down};
            default -> null;
        };
        if (at == null || keptAboveTheKeyboard.isEmpty()) {
            return true;
        }
        GobValue attached = keptAboveTheKeyboard.get();
        GobValue window = windowHolding(attached);
        int[] within = whereShown(window).mapBack(at[0], at[1]);
        double left = 0;
        double top = 0;
        for (GobStorage step = attached.storage(); step != null && step != window.storage(); step = step.parent()) {
            left += step.offset().x();
            top += step.offset().y();
        }
        PairValue size = attached.storage().size();
        return within[0] >= left && within[0] < left + size.x() && within[1] >= top && within[1] < top + size.y();
    }

    private void lowerKeyboard() {
        keptAboveTheKeyboard = Optional.empty();
        isPictureRequired = true;
        viewer.releaseTyping();
    }

    private void acceptMeasurements() {
        for (ScreenMeasurement next = measurements.poll(); next != null; next = measurements.poll()) {
            dispatchToInterpreter(next.width(), next.height());
        }
    }

    private void addressAllEvents() {
        for (ScreenPost next = posts.poll(); next != null; next = posts.poll()) {
            if (next.type() == ScreenEventType.DOWN && keptAboveTheKeyboard.isPresent()
                    && !isOnAttachedGob(next.detail())) {
                lowerKeyboard();
            }
            List<ScreenEvent> events = surface.address(next.type(), next.detail(), listWindowsFromBottomToTop(),
                    this::whereShown);
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
        fitResizableWindows();
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
    public void showSoftKeyboard(Optional<GobValue> attached) {
        keptAboveTheKeyboard = attached;
        isPictureRequired = true;
        viewer.takeTyping("");
    }

    @Override
    public void useCursor(ScreenCursor cursor) {
        viewer.useCursor(cursor);
    }

    public interface Viewer {
        boolean isConnected();
        void paint(List<DrawingCommand> painting);
        TextMeasurement textMeasurement(DefaultFont carried);
        default void takePostedLines() {
        }
        default void takeTyping(String heading) {
        }
        default void releaseTyping() {
        }
        default void useCursor(ScreenCursor cursor) {
        }
        default int screenDpi() {
            return CSS_PIXELS_PER_INCH;
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

    private enum SurfaceVisibility {
        HIDDEN,
        SHOWN
    }

}
