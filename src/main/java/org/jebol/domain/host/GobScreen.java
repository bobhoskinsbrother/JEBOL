package org.jebol.domain.host;

import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.Value;

import java.util.ArrayList;
import java.util.List;

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
    public List<ScreenEvent> takeQueuedEvents() {
        return queued.takeAll();
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
