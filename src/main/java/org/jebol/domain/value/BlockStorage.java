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

    public BlockStorage() {
        this.items = new ArrayList<>();
    }

    public BlockStorage(Collection<? extends Value> initialItems) {
        this.items = new ArrayList<>(initialItems);
    }

    public static BlockStorage of(Value... initialItems) {
        return new BlockStorage(List.of(initialItems));
    }

    public int length() {
        return items.size();
    }

    /** The value at a 1-based position. */
    public Value at(int oneBasedIndex) {
        return items.get(oneBasedIndex - 1);
    }

    public void set(int oneBasedIndex, Value value) {
        items.set(oneBasedIndex - 1, value);
    }

    public void append(Value value) {
        items.add(value);
    }

    public void insertAt(int oneBasedIndex, Value value) {
        items.add(oneBasedIndex - 1, value);
    }

    public Value removeAt(int oneBasedIndex) {
        return items.remove(oneBasedIndex - 1);
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
