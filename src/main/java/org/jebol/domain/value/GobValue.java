package org.jebol.domain.value;

/**
 * A position into a gob's pane, which is what makes a gob a series.
 *
 * <p>The index walks the children rather than anything about the gob itself, so
 * {@code next gob} is the same gob standing at its second child. That is why the
 * gob's own fields are reached through a path and never through a position.
 */
public record GobValue(GobStorage storage, int index) implements SeriesValue {

    public GobValue {
        if (storage == null) {
            throw new IllegalArgumentException("a gob value needs storage");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    /** A gob with nothing in it, at zero, of no size. What `Make_Gob` gives. */
    public static GobValue empty() {
        return new GobValue(new GobStorage(), 1);
    }

    @Override
    public Datatype datatype() {
        return Datatype.GOB;
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public GobValue atIndex(int oneBasedIndex) {
        return new GobValue(storage, oneBasedIndex);
    }

    @Override
    public GobValue head() {
        return atIndex(1);
    }

    @Override
    public GobValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(SeriesValue other) {
        return other instanceof GobValue gob && gob.storage == storage;
    }

    /**
     * Two gobs are the same gob or they are not equal.
     *
     * <p>Identity rather than contents, because a gob is a thing on a screen with
     * a parent and a pane: two gobs with the same fields are two objects, and
     * comparing their panes would recurse through a tree that can hold itself.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof GobValue gob
                && gob.storage == storage
                && gob.index == index;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(storage) * 31 + index;
    }

    @Override
    public String toString() {
        return "gob " + storage.offset() + " @" + index;
    }
}
