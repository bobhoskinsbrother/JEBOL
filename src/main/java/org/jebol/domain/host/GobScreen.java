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
    public void takeTheRootGob(GobValue given) {
        this.root = given;
    }

    /**
     * Leaves an event where a toolkit's own thread may put it, to be moved
     * into the interpreter's queue when the interpreter asks for one.
     */
    protected final void posted(ScreenEventKind kind, GobValue window) {
        postedFromAnotherThread.add(new ScreenEvent(kind, window));
    }

    /** The same, for an event that carries where it happened or which key. */
    protected final void posted(ScreenEventKind kind, GobValue window, ScreenEventDetail detail) {
        postedFromAnotherThread.add(new ScreenEvent(kind, window, detail));
    }

    @Override
    public Optional<ScreenEvent> takeTheNextEvent() {
        moveEverythingPostedFromAnotherThread();
        return queued.takeTheOldest();
    }

    private void moveEverythingPostedFromAnotherThread() {
        for (ScreenEvent next = postedFromAnotherThread.poll();
                next != null;
                next = postedFromAnotherThread.poll()) {
            queued.add(next.kind(), next.window(), next.detail());
        }
    }

    @Override
    public void show(GobValue gob) {
        if (!hasADisplay()) {
            throw nothingToShowOn();
        }
        if (root == null) {
            return;
        }
        if (gob.sharesStorageWith(root)) {
            closeWhatLeftThePaneBeforeOpeningWhatArrivedInIt();
            return;
        }
        if (isInTheRootsPane(gob)) {
            openOrRepaint(gob);
            return;
        }
        if (hasAWindow(gob)) {
            closeTheWindowFor(gob);
            return;
        }
        theWindowHolding(gob).filter(this::hasAWindow).ifPresent(this::repaintTheWindowFor);
    }

    protected abstract Denied nothingToShowOn();

    protected abstract List<GobValue> gobsWithWindows();

    protected abstract void openTheWindowFor(GobValue gob);

    protected abstract void repaintTheWindowFor(GobValue gob);

    protected abstract void closeTheWindowFor(GobValue gob);

    protected boolean hasAWindow(GobValue gob) {
        return gobsWithWindows().stream().anyMatch(gob::sharesStorageWith);
    }

    private void closeWhatLeftThePaneBeforeOpeningWhatArrivedInIt() {
        for (GobValue standing : List.copyOf(gobsWithWindows())) {
            if (!isInTheRootsPane(standing)) {
                closeTheWindowFor(standing);
            }
        }
        for (GobValue child : childrenOfTheRoot()) {
            openOrRepaint(child);
        }
    }

    private void openOrRepaint(GobValue gob) {
        if (hasAWindow(gob)) {
            repaintTheWindowFor(gob);
            return;
        }
        openTheWindowFor(gob);
    }

    private Optional<GobValue> theWindowHolding(GobValue gob) {
        GobStorage outermost = gob.storage();
        while (outermost.parent() != null && outermost.parent() != root.storage()) {
            outermost = outermost.parent();
        }
        if (outermost.parent() == null) {
            return Optional.empty();
        }
        GobStorage inTheRootsPane = outermost;
        return childrenOfTheRoot().stream()
                .filter(child -> child.storage() == inTheRootsPane)
                .findFirst();
    }

    protected List<GobValue> childrenOfTheRoot() {
        List<GobValue> children = new ArrayList<>();
        for (Value child : root.storage().pane()) {
            if (child instanceof GobValue held) {
                children.add(held);
            }
        }
        return List.copyOf(children);
    }

    protected boolean isInTheRootsPane(GobValue gob) {
        return childrenOfTheRoot().stream().anyMatch(gob::sharesStorageWith);
    }
}
