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
 * A browser as a third screen, not a second dialect.
 *
 * <p>What stood here before read a layout block straight into markup and never
 * made a gob at all. It worked, and it was a second implementation of the
 * dialect rather than a third renderer of one: the two would drift, and only
 * one of them was what REBOL's own library talks to.
 *
 * <p>So a browser implements the same port a desktop window does. VIEW,
 * UNVIEW, DO-EVENTS and the handler list are the same borrowed REBOL either
 * way, and the only difference is which adapter executes the paint list.
 *
 * <p>Nothing here mentions HTTP, and that is deliberate rather than
 * unfinished. This hands over a paint list and takes events back; whether that
 * travels as server-sent events, over a socket, or through the host's own web
 * framework belongs to {@link BrowserScreen.Viewer}, because JEBOL exists to
 * run inside a host that already has one and a transport chosen here would be
 * a decision a language made for every host.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
public final class BrowserScreen implements ScreenPort {

    /**
     * Whoever is looking at the page, and however the picture reaches them.
     *
     * <p>The whole of the transport, as two questions. A host that already
     * serves pages implements this over what it has; {@link WebScreenServer}
     * implements it over the JDK's own HTTP server for a host that has not.
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

    /**
     * A browser with nobody looking is a screen that is not there.
     *
     * <p>The same split every screen makes, and for the same reason: a host
     * serving a page nobody has opened is in exactly the position of a machine
     * with no display.
     */
    @Override
    public boolean hasADisplay() {
        return viewer.isConnected();
    }

    @Override
    public int displayCount() {
        return hasADisplay() ? 1 : 0;
    }

    /**
     * What the browser last said its viewport measures.
     *
     * <p>It resizes the root gob as well as remembering the number, and that
     * is not a convenience. A browser reports its size after it has attached
     * and reports it again whenever the window is dragged, so a root sized
     * once when the screen was handed over would be wrong from the first
     * resize onwards -- and a root of no size clips the whole page away with
     * nothing anywhere saying why.
     */
    public void theBrowserMeasures(int wide, int high) {
        this.viewport = PairValue.of(wide, high);
        if (root != null) {
            root.storage().size(viewport);
        }
    }

    /**
     * What a page measures.
     *
     * <p>A page has no title bar, no window frame and no minimum, so five of
     * the twelve are honestly zero rather than guessed at. Its usable area is
     * the whole of it, which is the one place a browser is simpler than a
     * desktop rather than more complicated.
     */
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

    /**
     * Reconciles, then paints the whole page again.
     *
     * <p>Painted again rather than patched, which is the one place a browser
     * differs from a window in what it is told. A window is asked to refresh
     * itself and keeps what it had; a page is handed the picture. The picture
     * is a function of the gob tree, so building it afresh cannot drift from
     * the tree, where a patch that went wrong would leave the two disagreeing
     * with nothing to notice.
     */
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
        viewer.paint(everythingOnThePage());
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

    /**
     * Every window on the page, as one list.
     *
     * <p>A browser has one surface and a desktop has many, so the windows are
     * flattened into the root's own paint list rather than sent one at a time.
     * What that costs is that a page cannot stack windows the way an operating
     * system does; what it buys is that the picture is one thing, which is
     * what makes it comparable with what a window shows.
     */
    private PaintList everythingOnThePage() {
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

    /**
     * Something the person looking at the page did.
     *
     * <p>Queued and not acted on, exactly as a toolkit's own thread queues.
     * The round trip is real and the script cannot see it: the interpreter's
     * own thread takes this inside WAIT and the handlers run where they always
     * run, so one handler block works on a desktop and in a browser without
     * knowing which it is under.
     */
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
