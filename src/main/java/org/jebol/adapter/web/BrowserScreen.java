package org.jebol.adapter.web;

import org.jebol.domain.eval.ScreenEvent;
import org.jebol.domain.eval.ScreenEventKind;
import org.jebol.domain.eval.ScreenMetric;
import org.jebol.domain.eval.ScreenPort;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.PairValue;
import org.jebol.domain.value.Value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A browser as a third screen, implementing the same port a desktop window
 * does. Nothing here mentions HTTP: this hands over a paint list and takes
 * events back, and how that travels belongs to {@link BrowserScreen.Viewer}.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public final class BrowserScreen implements ScreenPort {

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
    private final Deque<ScreenEvent> queued = new ArrayDeque<>();
    private final List<GobValue> showing = new ArrayList<>();

    private GobValue root;
    private PairValue viewport = PairValue.of(0, 0);
    private org.jebol.domain.value.ObjectValue drawDialect;

    @Override
    public void useDrawDialect(Value dialect) {
        this.drawDialect = dialect instanceof org.jebol.domain.value.ObjectValue named
                ? named
                : null;
    }

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
        this.root = given;
        if (!viewport.equals(PairValue.of(0, 0))) {
            given.storage().size(viewport);
        }
    }

    @Override
    public void show(GobValue gob) {
        if (!hasADisplay()) {
            throw new Denied("no-service",
                    "no browser is attached to this screen");
        }
        if (root == null) {
            return;
        }
        reconcile(gob);
        viewer.paint(theWholePagePaintedAfreshRatherThanPatched());
    }

    private void reconcile(GobValue gob) {
        if (gob.sharesStorageWith(root)) {
            showing.clear();
            showing.addAll(childrenOfTheRoot());
            return;
        }
        showing.removeIf(gob::sharesStorageWith);
        if (isInTheRootsPane(gob)) {
            showing.add(gob);
        }
    }

    private PaintList theWholePagePaintedAfreshRatherThanPatched() {
        return PaintList.ofTheScreen(root,
                (int) Math.round(viewport.x()), (int) Math.round(viewport.y()),
                drawDialect);
    }

    private List<GobValue> childrenOfTheRoot() {
        List<GobValue> children = new ArrayList<>();
        for (Value child : root.storage().pane()) {
            if (child instanceof GobValue held) {
                children.add(held);
            }
        }
        return List.copyOf(children);
    }

    private boolean isInTheRootsPane(GobValue gob) {
        return childrenOfTheRoot().stream().anyMatch(gob::sharesStorageWith);
    }

    /** The gobs this page currently has windows for. */
    public List<GobValue> whatIsShowing() {
        return List.copyOf(showing);
    }

    /** Something the person looking at the page did, queued and not acted on. */
    public synchronized void theBrowserReports(ScreenEventKind kind, GobValue window) {
        queued.add(new ScreenEvent(kind, window));
    }

    @Override
    public synchronized List<ScreenEvent> takeQueuedEvents() {
        List<ScreenEvent> taken = List.copyOf(queued);
        queued.clear();
        return taken;
    }
}
