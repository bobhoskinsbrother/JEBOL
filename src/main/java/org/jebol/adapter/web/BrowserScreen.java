package org.jebol.adapter.web;

import org.jebol.adapter.fonts.JavaTextMeasure;
import org.jebol.domain.host.ScreenEvent;
import org.jebol.domain.host.ScreenEventDetail;
import org.jebol.domain.host.ScreenPort;
import org.jebol.domain.host.WindowsOnOneSurface;
import org.jebol.domain.render.TextMeasure;
import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A browser as a third screen, implementing the same port a desktop window
 * does. Nothing here mentions HTTP: this hands over a paint list and takes
 * events back, and how that travels belongs to {@link BrowserScreen.Viewer}.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public final class BrowserScreen extends GobScreen {

    /**
     * Whoever is looking at the page, and however the picture reaches them --
     * the whole of the transport, as two questions.
     */
    public interface Viewer {

        /** Whether a browser is attached right now. */
        boolean isConnected();

        /** Here is the picture. Paint it. */
        void paint(PaintList painting);
    }

    private final Viewer viewer;
    private final JavaTextMeasure measure = new JavaTextMeasure();
    private final List<GobValue> showing = new ArrayList<>();
    private final WindowsOnOneSurface surface = new WindowsOnOneSurface();
    private final Queue<Posted> postedByThePage = new ConcurrentLinkedQueue<>();

    private record Posted(ScreenEventKind kind, ScreenEventDetail detail) {
    }

    private PairValue viewport = PairValue.of(0, 0);

    private BrowserScreen(Viewer viewer) {
        this.viewer = viewer;
    }

    public static BrowserScreen seenBy(Viewer viewer) {
        return new BrowserScreen(viewer);
    }

    @Override
    public boolean hasADisplay() {
        return viewer.isConnected();
    }

    @Override
    public int displayCount() {
        return hasADisplay() ? 1 : 0;
    }

    /** What the browser last said its viewport measures, resizing the root with it. */
    public void theBrowserMeasures(int wide, int high) {
        this.viewport = PairValue.of(wide, high);
        if (root != null) {
            root.storage().size(viewport);
        }
    }

    @Override
    public PairValue measure(ScreenMetric metric, int display) {
        if (!hasADisplay()) {
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
    public void takeTheRootGob(GobValue given) {
        super.takeTheRootGob(given);
        if (!viewport.equals(PairValue.of(0, 0))) {
            given.storage().size(viewport);
        }
    }

    @Override
    public void show(GobValue gob) {
        super.show(gob);
        if (root != null) {
            viewer.paint(theWholePagePaintedAfreshRatherThanPatched());
        }
    }

    @Override
    public TextMeasure textMeasure() {
        return hasADisplay() ? measure : ScreenPort.none().textMeasure();
    }

    @Override
    protected Denied nothingToShowOn() {
        return new Denied("no-service", "no browser is attached to this screen");
    }

    @Override
    protected List<GobValue> gobsWithWindows() {
        return List.copyOf(showing);
    }

    @Override
    protected void openTheWindowFor(GobValue gob) {
        showing.add(gob);
    }

    @Override
    protected void repaintTheWindowFor(GobValue gob) {
    }

    @Override
    protected void closeTheWindowFor(GobValue gob) {
        showing.removeIf(gob::sharesStorageWith);
    }

    private PaintList theWholePagePaintedAfreshRatherThanPatched() {
        return PaintList.ofTheScreen(root,
                (int) Math.round(viewport.x()), (int) Math.round(viewport.y()),
                drawDialect);
    }

    public void theBrowserReports(ScreenEventKind kind, ScreenEventDetail detail) {
        postedByThePage.add(new Posted(kind, detail));
    }

    @Override
    public Optional<ScreenEvent> takeTheNextEvent() {
        addressEverythingThePagePosted();
        return super.takeTheNextEvent();
    }

    private void addressEverythingThePagePosted() {
        for (Posted next = postedByThePage.poll(); next != null; next = postedByThePage.poll()) {
            for (ScreenEvent addressed : surface.addressed(next.kind(), next.detail(), windowsShowingFromBottomToTop())) {
                queued.add(addressed.kind(), addressed.window(), addressed.detail());
            }
        }
    }

    private List<GobValue> windowsShowingFromBottomToTop() {
        if (root == null) {
            return List.of();
        }
        return childrenOfTheRoot().stream().filter(this::hasAWindow).toList();
    }
}
