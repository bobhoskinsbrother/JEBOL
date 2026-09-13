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
    }

    /**
     * Where this position counts from, as an unsigned thirty-two bit number.
     *
     * <p>A gob's position is nothing like a series'. {@code VAL_GOB_INDEX} is a
     * {@code REBCNT}, and no arm of the C clamps it, so stepping back past the
     * head wraps round rather than stopping: {@code index? skip g -2} answers
     * 4294967295. Every operation that then uses the position clamps to the
     * pane, which is why an insert at a wrapped position appends.
     */
    public long positionCountedAsUnsigned() {
        return Integer.toUnsignedLong(index);
    }

    /**
     * The same position brought back inside the pane, which is what every arm
     * that reads or writes a child does with it.
     *
     * <p>A position stepped back past the head is a huge unsigned number
     * rather than a small one, so it clamps to the tail and not to the head.
     * That is why {@code move g -1} takes the first child and puts it last.
     */
    public int positionWithinThePane() {
        long counted = Integer.toUnsignedLong(index - 1) + 1;
        return (int) Math.min(Math.max(counted, 1), storage.length() + 1L);
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
