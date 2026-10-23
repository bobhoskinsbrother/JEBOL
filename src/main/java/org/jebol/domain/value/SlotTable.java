package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

final class SlotTable {

    private static final int LONGEST_SCANNED_IN_ORDER = 8;
    private static final int SMALLEST_CAPACITY = 4;

    private String[] canonicalNames;
    private ContextSlot[] slots;
    private int size;
    private Map<String, Integer> positionByCanonicalName = Map.of();

    SlotTable() {
        this(SMALLEST_CAPACITY);
    }

    SlotTable(int expected) {
        int capacity = Math.max(expected, 1);
        canonicalNames = new String[capacity];
        slots = new ContextSlot[capacity];
    }

    ContextSlot get(String canonicalName) {
        int position = findPosition(canonicalName);
        return position < 0 ? null : slots[position];
    }

    ContextSlot at(int position) {
        if (position < 0 || position >= size) {
            throw new IndexOutOfBoundsException("no slot at " + position + " of " + size);
        }
        return slots[position];
    }

    boolean containsKey(String canonicalName) {
        return findPosition(canonicalName) >= 0;
    }

    void add(String canonicalName, ContextSlot slot) {
        if (size == slots.length) {
            grow();
        }
        canonicalNames[size] = canonicalName;
        slots[size] = slot;
        if (isIndexed()) {
            positionByCanonicalName.put(canonicalName, size);
        }
        size++;
        if (size == LONGEST_SCANNED_IN_ORDER + 1) {
            indexEveryName();
        }
    }

    int size() {
        return size;
    }

    List<ContextSlot> values() {
        List<ContextSlot> inOrder = new ArrayList<>(size);
        inOrder.addAll(Arrays.asList(slots).subList(0, size));
        return inOrder;
    }

    void forEach(BiConsumer<String, ContextSlot> visit) {
        for (int position = 0; position < size; position++) {
            visit.accept(canonicalNames[position], slots[position]);
        }
    }

    private int findPosition(String canonicalName) {
        if (isIndexed()) {
            Integer position = positionByCanonicalName.get(canonicalName);
            return position == null ? -1 : position;
        }
        for (int position = 0; position < size; position++) {
            if (canonicalNames[position].equals(canonicalName)) {
                return position;
            }
        }
        return -1;
    }

    private boolean isIndexed() {
        return size > LONGEST_SCANNED_IN_ORDER;
    }

    private void indexEveryName() {
        positionByCanonicalName = new HashMap<>(size * 2);
        for (int position = 0; position < size; position++) {
            positionByCanonicalName.put(canonicalNames[position], position);
        }
    }

    private void grow() {
        int capacity = Math.max(SMALLEST_CAPACITY, slots.length * 2);
        canonicalNames = Arrays.copyOf(canonicalNames, capacity);
        slots = Arrays.copyOf(slots, capacity);
    }
}
