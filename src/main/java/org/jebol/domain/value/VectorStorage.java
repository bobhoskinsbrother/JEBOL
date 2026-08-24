package org.jebol.domain.value;

import java.util.Arrays;

/**
 * The mutable buffer behind a {@code vector!} value.
 *
 * <p>Every element is held as the bits that would sit in memory at the
 * vector's own width, already reduced by {@link VectorKind#store}. That is
 * what makes a vector a vector: the width is decided once for the whole
 * buffer, so nothing here needs to ask what kind a particular element is.
 */
public final class VectorStorage {

    private static final int INITIAL_CAPACITY = 8;

    private final VectorKind kind;
    private long[] elements;
    private int length;
    private boolean isProtected;

    private final SeriesMemory.Reservation reserved;

    public VectorStorage(VectorKind kind, int howMany) {
        this.kind = kind;
        this.elements = new long[Math.max(INITIAL_CAPACITY, howMany)];
        this.length = howMany;
        this.reserved = SeriesMemory.reserve(this, bytesInTheBuffer());
    }

    private long bytesInTheBuffer() {
        return (long) elements.length * Long.BYTES;
    }

    public static VectorStorage holding(VectorKind kind, long... stored) {
        VectorStorage made = new VectorStorage(kind, stored.length);
        System.arraycopy(stored, 0, made.elements, 0, stored.length);
        return made;
    }

    public VectorKind kind() {
        return kind;
    }

    public int length() {
        return length;
    }

    public boolean isProtected() {
        return isProtected;
    }

    public void protectFromChange(boolean protectedNow) {
        this.isProtected = protectedNow;
    }

    private void refuseIfProtected() {
        if (isProtected) {
            throw new ProtectedFromChange();
        }
    }

    /** The stored bits of the element at a 1-based position. */
    public long at(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > length) {
            throw new IndexOutOfBoundsException(
                    "element " + oneBasedIndex + " outside 1.." + length);
        }
        return elements[oneBasedIndex - 1];
    }

    public void set(int oneBasedIndex, long stored) {
        refuseIfProtected();
        elements[oneBasedIndex - 1] = stored;
    }

    public void append(long stored) {
        refuseIfProtected();
        makeRoomForOneMore();
        elements[length] = stored;
        length++;
    }

    public void insertAt(int oneBasedIndex, long stored) {
        refuseIfProtected();
        makeRoomForOneMore();
        System.arraycopy(elements, oneBasedIndex - 1, elements, oneBasedIndex,
                length - oneBasedIndex + 1);
        elements[oneBasedIndex - 1] = stored;
        length++;
    }

    public long removeAt(int oneBasedIndex) {
        refuseIfProtected();
        long taken = elements[oneBasedIndex - 1];
        System.arraycopy(elements, oneBasedIndex, elements, oneBasedIndex - 1,
                length - oneBasedIndex);
        length--;
        return taken;
    }

    /** Drops everything from a 1-based position onwards, which is what CLEAR does. */
    public void clearFrom(int oneBasedIndex) {
        refuseIfProtected();
        if (oneBasedIndex <= length) {
            Arrays.fill(elements, oneBasedIndex - 1, length, 0L);
            length = oneBasedIndex - 1;
        }
    }

    private void makeRoomForOneMore() {
        if (length == elements.length) {
            elements = Arrays.copyOf(elements,
                    Math.max(INITIAL_CAPACITY, elements.length * 2));
            reserved.nowHolds(bytesInTheBuffer());
        }
    }

    public long[] snapshot() {
        return Arrays.copyOf(elements, length);
    }

    @Override
    public String toString() {
        return "VectorStorage(" + length + " " + kind.spelling() + ")";
    }
}
