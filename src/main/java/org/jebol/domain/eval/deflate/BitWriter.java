package org.jebol.domain.eval.deflate;

import java.util.Arrays;

final class BitWriter {

    private static final int SMALLEST_BUFFER = 64;

    private static final int HEADER_BITS_THAT_STILL_FIT_A_BYTE = 5;

    private byte[] held;

    private int used;

    private long pendingBits;

    private int pendingCount;

    BitWriter(int expectedLength) {
        held = new byte[Math.max(SMALLEST_BUFFER, expectedLength)];
    }

    int pendingCount() {
        return pendingCount;
    }

    void add(int bits, int count) {
        pendingBits |= Integer.toUnsignedLong(bits) << pendingCount;
        pendingCount += count;
        while (pendingCount >= Byte.SIZE) {
            write((int) pendingBits);
            pendingBits >>>= Byte.SIZE;
            pendingCount -= Byte.SIZE;
        }
    }

    void startStoredBlock(boolean finalBlock) {
        write(((finalBlock ? 1 : 0) << pendingCount) | (int) pendingBits);
        if (pendingCount > HEADER_BITS_THAT_STILL_FIT_A_BYTE) {
            write(0);
        }
        pendingBits = 0;
        pendingCount = 0;
    }

    void writeLittleEndianShort(int value) {
        write(value);
        write(value >>> Byte.SIZE);
    }

    void write(byte[] source, int from, int count) {
        makeRoomFor(count);
        System.arraycopy(source, from, held, used, count);
        used += count;
    }

    void write(int octet) {
        makeRoomFor(1);
        held[used++] = (byte) octet;
    }

    void finish() {
        if (pendingCount != 0) {
            write((int) pendingBits);
            pendingBits = 0;
            pendingCount = 0;
        }
    }

    byte[] toArray() {
        return Arrays.copyOf(held, used);
    }

    private void makeRoomFor(int count) {
        while (used + count > held.length) {
            held = Arrays.copyOf(held, held.length * 2);
        }
    }
}
