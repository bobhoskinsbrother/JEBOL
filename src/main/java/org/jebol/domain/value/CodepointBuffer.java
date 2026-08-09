package org.jebol.domain.value;

import java.util.Arrays;

/**
 * A growable sequence of Unicode codepoints.
 *
 * <p>REBOL strings are mutable series indexed by character with constant-time
 * access. {@link String} is immutable and indexed by UTF-16 code unit, so
 * {@code "a😀b".charAt(1)} is half an emoji. Neither property is negotiable
 * for a {@code string!}, so the storage is codepoints and this is it.
 */
final class CodepointBuffer {

    private static final int INITIAL_CAPACITY = 16;

    private int[] codepoints;
    private int length;

    CodepointBuffer() {
        this.codepoints = new int[INITIAL_CAPACITY];
        this.length = 0;
    }

    CodepointBuffer(String text) {
        this.codepoints = text.codePoints().toArray();
        this.length = codepoints.length;
    }

    int length() {
        return length;
    }

    int at(int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= length) {
            throw new IndexOutOfBoundsException(
                    "codepoint " + zeroBasedIndex + " outside 0.." + (length - 1));
        }
        return codepoints[zeroBasedIndex];
    }

    void set(int zeroBasedIndex, int codepoint) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= length) {
            throw new IndexOutOfBoundsException(
                    "codepoint " + zeroBasedIndex + " outside 0.." + (length - 1));
        }
        codepoints[zeroBasedIndex] = codepoint;
    }

    void append(int codepoint) {
        ensureCapacityFor(length + 1);
        codepoints[length] = codepoint;
        length++;
    }

    void insertAt(int zeroBasedIndex, int codepoint) {
        ensureCapacityFor(length + 1);
        System.arraycopy(codepoints, zeroBasedIndex, codepoints, zeroBasedIndex + 1,
                length - zeroBasedIndex);
        codepoints[zeroBasedIndex] = codepoint;
        length++;
    }

    int removeAt(int zeroBasedIndex) {
        int removed = at(zeroBasedIndex);
        System.arraycopy(codepoints, zeroBasedIndex + 1, codepoints, zeroBasedIndex,
                length - zeroBasedIndex - 1);
        length--;
        return removed;
    }

    String text(int zeroBasedFrom, int zeroBasedTo) {
        StringBuilder rendered = new StringBuilder(zeroBasedTo - zeroBasedFrom);
        for (int position = zeroBasedFrom; position < zeroBasedTo; position++) {
            rendered.appendCodePoint(codepoints[position]);
        }
        return rendered.toString();
    }

    private void ensureCapacityFor(int required) {
        if (required <= codepoints.length) {
            return;
        }
        int grown = Math.max(required, codepoints.length * 2);
        codepoints = Arrays.copyOf(codepoints, grown);
    }
}
