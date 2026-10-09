package org.jebol.domain.host;

import org.jebol.domain.value.GobStorage;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class GobScreen implements ScreenPort {

    protected final ScreenEventQueue queued = new ScreenEventQueue();
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

    @Override
    public Optional<ScreenEvent> takeTheNextEvent() {
        return queued.takeTheOldest();
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
