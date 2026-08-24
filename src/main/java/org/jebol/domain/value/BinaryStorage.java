package org.jebol.domain.value;

import java.util.Arrays;

/** The mutable buffer behind a {@code binary!} value. Octets, unsigned. */
public final class BinaryStorage {

    private static final int INITIAL_CAPACITY = 16;

    private byte[] bytes;
    private int length;
    private final SeriesMemory.Reservation reserved;

    public BinaryStorage() {
        this(INITIAL_CAPACITY);
    }

    public BinaryStorage(int roomFor) {
        this.bytes = new byte[Math.max(INITIAL_CAPACITY, roomFor)];
        this.length = 0;
        this.reserved = SeriesMemory.reserve(this, bytes.length);
    }

    public BinaryStorage(byte[] initialBytes) {
        this.bytes = initialBytes.clone();
        this.length = initialBytes.length;
        this.reserved = SeriesMemory.reserve(this, bytes.length);
    }

    public static BinaryStorage of(int... octets) {
        byte[] initial = new byte[octets.length];
        for (int position = 0; position < octets.length; position++) {
            initial[position] = requireOctet(octets[position]);
        }
        return new BinaryStorage(initial);
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
        return length;
    }

    /** The octet at a 1-based position, as an unsigned value from 0 to 255. */
    public int at(int oneBasedIndex) {
        if (oneBasedIndex < 1 || oneBasedIndex > length) {
            throw new IndexOutOfBoundsException(
                    "byte " + oneBasedIndex + " outside 1.." + length);
        }
        return Byte.toUnsignedInt(bytes[oneBasedIndex - 1]);
    }

    public void append(int octet) {
        refuseIfProtected();
        if (length == bytes.length) {
            bytes = Arrays.copyOf(bytes, Math.max(INITIAL_CAPACITY, bytes.length * 2));
            reserved.nowHolds(bytes.length);
        }
        bytes[length] = requireOctet(octet);
        length++;
    }

    /**
     * Replaces one octet.
     *
     * <p>This storage was append-only, which made a binary the one series
     * REVERSE and REMOVE could not touch. A binary is a series like any
     * other and the natives that take one should take it.
     */
    public void set(int oneBasedIndex, int octet) {
        refuseIfProtected();
        bytes[oneBasedIndex - 1] = (byte) octet;
    }

    public void insertAt(int oneBasedIndex, int octet) {
        refuseIfProtected();
        append(0);
        System.arraycopy(bytes, oneBasedIndex - 1, bytes, oneBasedIndex,
                length - oneBasedIndex);
        bytes[oneBasedIndex - 1] = (byte) octet;
    }

    public int removeAt(int oneBasedIndex) {
        refuseIfProtected();
        int taken = bytes[oneBasedIndex - 1] & 0xFF;
        System.arraycopy(bytes, oneBasedIndex, bytes, oneBasedIndex - 1,
                length - oneBasedIndex);
        length--;
        return taken;
    }

    public byte[] snapshot() {
        return Arrays.copyOf(bytes, length);
    }

    private static byte requireOctet(int octet) {
        if (octet < 0 || octet > 255) {
            throw new IllegalArgumentException("not an octet: " + octet);
        }
        return (byte) octet;
    }

    @Override
    public String toString() {
        return "BinaryStorage(" + length + " bytes)";
    }
}
