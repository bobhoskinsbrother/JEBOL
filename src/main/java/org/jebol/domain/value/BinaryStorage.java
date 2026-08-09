package org.jebol.domain.value;

import java.util.Arrays;

/** The mutable buffer behind a {@code binary!} value. Octets, unsigned. */
public final class BinaryStorage {

    private static final int INITIAL_CAPACITY = 16;

    private byte[] bytes;
    private int length;

    public BinaryStorage() {
        this.bytes = new byte[INITIAL_CAPACITY];
        this.length = 0;
    }

    public BinaryStorage(byte[] initialBytes) {
        this.bytes = initialBytes.clone();
        this.length = initialBytes.length;
    }

    public static BinaryStorage of(int... octets) {
        byte[] initial = new byte[octets.length];
        for (int position = 0; position < octets.length; position++) {
            initial[position] = requireOctet(octets[position]);
        }
        return new BinaryStorage(initial);
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
        if (length == bytes.length) {
            bytes = Arrays.copyOf(bytes, Math.max(INITIAL_CAPACITY, bytes.length * 2));
        }
        bytes[length] = requireOctet(octet);
        length++;
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
