package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A map: key-to-value storage, written {@code #[key: value ...]}.
 *
 * <p>Not a series. It has no position, so NEXT and FIRST mean nothing on
 * one, and LENGTH? counts pairs rather than items. Reading a key it has not
 * got gives NONE rather than raising, which is the opposite of an object
 * and the same as a series position past the end: a map is asked about keys
 * it may not have, and an object is asked for fields it should have.
 *
 * <p>Keys are values rather than names, so {@code #[1 2]} has the integer
 * one as a key. That is the whole difference from an object, and it is why
 * this does not reuse {@link Context}.
 *
 * <p>Mutable and shared, as series storage is: two references to the same
 * map see each other's changes. {@link LinkedHashMap} keeps insertion order
 * so that MOLD and KEYS-OF produce a stable answer, which the tests depend
 * on even though order is not part of equality.
 */
public final class MapValue implements Value {

    private final Map<Value, Value> entries;

    private MapValue(Map<Value, Value> entries) {
        this.entries = entries;
    }

    public static MapValue empty() {
        return new MapValue(new LinkedHashMap<>());
    }

    /**
     * A map from a block of alternating keys and values.
     *
     * <p>An odd number of items is a mistake rather than a key with no
     * value: {@code #[none]} is malformed, not a map holding NONE. Padding
     * it would make a typo into a map nobody meant.
     */
    public static MapValue of(List<Value> pairs) {
        if (pairs.size() % 2 != 0) {
            throw new IllegalArgumentException(
                    "a map needs a value for every key, and got " + pairs.size() + " items");
        }
        Map<Value, Value> built = new LinkedHashMap<>();
        for (int at = 0; at < pairs.size(); at += 2) {
            built.put(keyOf(pairs.get(at)), pairs.get(at + 1));
        }
        return new MapValue(built);
    }

    /**
     * A set-word key is stored as the plain word it names.
     *
     * <p>{@code #[a: 1]} and {@code make map! [a 1]} hold the same key, so
     * writing the literal with a colon must not produce a different map from
     * building one without.
     */
    private static Value keyOf(Value written) {
        return written instanceof WordValue word && word.datatype() == Datatype.SET_WORD
                ? word.as(Datatype.WORD)
                : written;
    }

    /** What a key holds, or NONE when the map has not got it. */
    public Value select(Value key) {
        return entries.getOrDefault(keyOf(key), NoneValue.none());
    }

    public boolean holds(Value key) {
        return entries.containsKey(keyOf(key));
    }

    /** Adds or replaces a key, in place. */
    public void put(Value key, Value value) {
        entries.put(keyOf(key), value);
    }

    public void remove(Value key) {
        entries.remove(keyOf(key));
    }

    /** Pairs, not items: {@code #[a: 1 b: 2]} is two long. */
    public int pairCount() {
        return entries.size();
    }

    public List<Value> keys() {
        return List.copyOf(entries.keySet());
    }

    public List<Value> values() {
        return List.copyOf(entries.values());
    }

    /** The pairs in order, as a flat list, which is how MOLD walks them. */
    public List<Value> flattened() {
        List<Value> flat = new ArrayList<>();
        entries.forEach((key, value) -> {
            flat.add(key);
            flat.add(value);
        });
        return List.copyOf(flat);
    }

    public MapValue copy() {
        return new MapValue(new LinkedHashMap<>(entries));
    }

    /** Same keys with equal values. Insertion order plays no part. */
    @Override
    public boolean equals(Object other) {
        return other instanceof MapValue map && entries.equals(map.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public Datatype datatype() {
        return Datatype.MAP;
    }

    @Override
    public String toString() {
        return "map of " + entries.size() + " pairs";
    }
}
