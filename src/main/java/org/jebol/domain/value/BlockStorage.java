package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The mutable buffer behind every {@code block!}, {@code paren!} and path.
 *
 * <p>Identity matters here and equality does not: two storages holding the
 * same values are still two storages, and only the values pointing at one of
 * them see its mutations. So this deliberately does not override
 * {@code equals}.
 */
public final class BlockStorage {

    private final List<Value> items;

    /**
     * The positions carrying a line break, one-based.
     *
     * <p>A marker is a property of a position rather than a value in the
     * block, which is why NEW-LINE and NEW-LINE? are natives of their own
     * rather than something you could write by inserting a value. It
     * survives MOLD and is what makes a molded block keep its shape.
     */
    private final Set<Integer> lineBreaks = new HashSet<>();

    public BlockStorage() {
        this.items = new ArrayList<>();
    }

    /**
     * A block of values, and never a block with a hole in it.
     *
     * <p>Refused at the door rather than tolerated further in. A null here is
     * absence smuggled in as a value, and it does not announce itself: it
     * survives every walk that only looks at what a value is, and surfaces
     * somewhere else entirely as a NullPointerException from a copy. One got
     * into a block read out of struct-test.r3 and came back out of
     * {@code remaining()} in the test harness, a whole file away from
     * whatever put it there.
     */
    public BlockStorage(Collection<? extends Value> initialItems) {
        int at = 0;
        for (Value item : initialItems) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "a block cannot hold null: position " + (at + 1) + " of "
                                + initialItems.size() + " is absent rather than a value, "
                                + "and unset! is how REBOL says that");
            }
            at++;
        }
        this.items = new ArrayList<>(initialItems);
    }

    public static BlockStorage of(Value... initialItems) {
        return new BlockStorage(List.of(initialItems));
    }

    /**
     * Whether this storage refuses modification.
     *
     * <p>On the storage rather than on the value, because two series
     * values sharing storage are two views of one thing and cannot
     * disagree about whether it can change. PROTECT of either protects
     * both, which is what makes protection worth anything.
     */
    private boolean isProtected;

    /**
     * Stops a change to protected storage.
     *
     * <p>Here rather than in the natives, because every mutation passes
     * through this class and a check per native is a check that can be
     * left off the next one.
     */
    private void refuseIfProtected() {
        if (isProtected) {
            throw new ProtectedFromChange();
        }
    }

    public boolean isProtected() {
        return isProtected;
    }

    public void protectFromChange(boolean protectedNow) {
        this.isProtected = protectedNow;
    }

    public int length() {
        return items.size();
    }

    /** The value at a 1-based position. */
    public Value at(int oneBasedIndex) {
        return items.get(oneBasedIndex - 1);
    }

    public void set(int oneBasedIndex, Value value) {
        refuseIfProtected();
        items.set(oneBasedIndex - 1, value);
    }

    public void append(Value value) {
        refuseIfProtected();
        items.add(value);
    }

    public void insertAt(int oneBasedIndex, Value value) {
        refuseIfProtected();
        items.add(oneBasedIndex - 1, value);
    }

    public Value removeAt(int oneBasedIndex) {
        refuseIfProtected();
        return items.remove(oneBasedIndex - 1);
    }

    /** Whether the position carries a line break. */
    public boolean breaksLineAt(int oneBasedIndex) {
        return lineBreaks.contains(oneBasedIndex);
    }

    /**
     * The line-start flags of another storage, counted from a position in it.
     *
     * <p>A flag belongs to a position rather than to a value, so anything
     * that builds a new storage out of an old one has to carry them across or
     * they are gone. Binding is where that first mattered: it copies the
     * block, and a bound block molded on one line however its author had laid
     * it out, which is every block a script runs.
     */
    public void takeLineBreaksFrom(BlockStorage older, int startingAt) {
        for (int at = startingAt; at <= older.length() + 1; at++) {
            if (older.breaksLineAt(at)) {
                setLineBreakAt(at - startingAt + 1, true);
            }
        }
    }

    public void setLineBreakAt(int oneBasedIndex, boolean breaks) {
        if (breaks) {
            lineBreaks.add(oneBasedIndex);
        } else {
            lineBreaks.remove(oneBasedIndex);
        }
    }

    /** A snapshot. Mutating the result does not touch this storage. */
    public List<Value> snapshot() {
        return List.copyOf(items);
    }

    @Override
    public String toString() {
        return "BlockStorage(" + items.size() + " items)";
    }
}
