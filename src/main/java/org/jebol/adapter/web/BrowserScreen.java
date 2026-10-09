package org.jebol.adapter.web;

import org.jebol.domain.host.ScreenEventKind;
import org.jebol.domain.host.GobScreen;
import org.jebol.domain.host.ScreenMetric;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;

import java.util.ArrayList;
import java.util.List;

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
    private final List<GobValue> showing = new ArrayList<>();

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

    /** The gobs this page currently has windows for. */
    public List<GobValue> whatIsShowing() {
        return List.copyOf(showing);
    }

    /** Something the person looking at the page did, queued and not acted on. */
    public void theBrowserReports(ScreenEventKind kind, GobValue window) {
        queued.add(kind, window);
    }
}
