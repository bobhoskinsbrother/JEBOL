package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
    private final java.util.Set<Integer> lineBreaks = new java.util.HashSet<>();

    public BlockStorage() {
        this.items = new ArrayList<>();
    }

    public BlockStorage(Collection<? extends Value> initialItems) {
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
