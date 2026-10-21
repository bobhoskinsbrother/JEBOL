package org.jebol.domain.host;

import org.jebol.domain.value.GobStorage;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class GobScreen implements ScreenPort {

    protected final ScreenEventQueue queued = new ScreenEventQueue();
    private final Queue<ScreenEvent> postedFromAnotherThread = new ConcurrentLinkedQueue<>();
    protected GobValue root;
    protected ObjectValue drawDialect;

    @Override
    public void useDrawDialect(Value dialect) {
        this.drawDialect = dialect instanceof ObjectValue given
                ? given
                : null;
    }

    @Override
    public void acceptRootGob(GobValue given) {
        this.root = given;
    }

    /**
     * Leaves an event where a toolkit's own thread may put it, to be moved
     * into the interpreter's queue when the interpreter asks for one.
     */
    protected final void post(ScreenEventType kind, GobValue window) {
        postedFromAnotherThread.add(new ScreenEvent(kind, window));
    }

    /** The same, for an event that carries where it happened or which key. */
    protected final void post(ScreenEventType kind, GobValue window, ScreenEventDetail detail) {
        postedFromAnotherThread.add(new ScreenEvent(kind, window, detail));
    }

    @Override
    public Optional<ScreenEvent> acceptNextEvent() {
        moveEverythingPostedFromAnotherThread();
        return queued.takeTheOldest();
    }

    private void moveEverythingPostedFromAnotherThread() {
        for (ScreenEvent next = postedFromAnotherThread.poll();
                next != null;
                next = postedFromAnotherThread.poll()) {
            queued.add(next.type(), next.window(), next.detail());
        }
    }

    @Override
    public void show(GobValue gob) {
        if (!hasDisplay()) {
            throw refuseWithNoService();
        }
        if (root == null) {
            return;
        }
        if (gob.sharesStorageWith(root)) {
            closeWhatLeftThePaneBeforeOpeningWhatArrivedInIt();
            return;
        }
        if (isInRootsPane(gob)) {
            openOrRepaint(gob);
            return;
        }
        if (hasWindow(gob)) {
            closeWindowFor(gob);
            return;
        }
        findWindowHolding(gob).filter(this::hasWindow).ifPresent(this::repaintWindowFor);
    }

    protected abstract Denied refuseWithNoService();

    protected abstract List<GobValue> gobWithWindow();

    protected abstract void openWindowFor(GobValue gob);

    protected abstract void repaintWindowFor(GobValue gob);

    protected abstract void closeWindowFor(GobValue gob);

    protected boolean hasWindow(GobValue gob) {
        return gobWithWindow().stream().anyMatch(gob::sharesStorageWith);
    }

    private void closeWhatLeftThePaneBeforeOpeningWhatArrivedInIt() {
        for (GobValue standing : List.copyOf(gobWithWindow())) {
            if (!isInRootsPane(standing)) {
                closeWindowFor(standing);
            }
        }
        for (GobValue child : listTheChildrenOfTheRoot()) {
            openOrRepaint(child);
        }
    }

    private void openOrRepaint(GobValue gob) {
        if (hasWindow(gob)) {
            repaintWindowFor(gob);
            return;
        }
        openWindowFor(gob);
    }

    private Optional<GobValue> findWindowHolding(GobValue gob) {
        GobStorage outermost = gob.storage();
        while (outermost.parent() != null && outermost.parent() != root.storage()) {
            outermost = outermost.parent();
        }
        if (outermost.parent() == null) {
            return Optional.empty();
        }
        GobStorage inTheRootsPane = outermost;
        return listTheChildrenOfTheRoot().stream()
                .filter(child -> child.storage() == inTheRootsPane)
                .findFirst();
    }

    protected List<GobValue> listTheChildrenOfTheRoot() {
        List<GobValue> children = new ArrayList<>();
        for (Value child : root.storage().pane()) {
            if (child instanceof GobValue held) {
                children.add(held);
            }
        }
        return List.copyOf(children);
    }

    protected boolean isInRootsPane(GobValue gob) {
        return listTheChildrenOfTheRoot().stream().anyMatch(gob::sharesStorageWith);
    }
}
