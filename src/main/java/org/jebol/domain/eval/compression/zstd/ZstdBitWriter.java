package org.jebol.domain.eval.compression.zstd;

final class ZstdBitWriter {

    private static final int CONTAINER_BYTES = 8;

    private byte[] output;
    private int start;
    private int pointer;
    private int end;
    private long container;
    private int position;

    boolean open(byte[] buffer, int at, int capacity) {
        output = buffer;
        start = at;
        pointer = at;
        end = at + capacity - CONTAINER_BYTES;
        container = 0;
        position = 0;
        return capacity > CONTAINER_BYTES;
    }

    void addBits(long value, int count) {
        long kept = count == 64 ? value : value & ((1L << count) - 1);
        container |= kept << position;
        position += count;
    }

    void addBitsFast(long value, int count) {
        container |= value << position;
        position += count;
    }

    void flush() {
        int octets = position >>> 3;
        writeContainer();
        pointer += octets;
        if (pointer > end) {
            pointer = end;
        }
        position &= 7;
        container = octets == 8 ? 0 : container >>> (octets * 8);
    }

    void flushFast() {
        int octets = position >>> 3;
        writeContainer();
        pointer += octets;
        position &= 7;
        container = octets == 8 ? 0 : container >>> (octets * 8);
    }

    int close() {
        addBitsFast(1, 1);
        flush();
        if (pointer >= end) {
            return 0;
        }
        return pointer - start + (position > 0 ? 1 : 0);
    }

    private void writeContainer() {
        int room = Math.min(CONTAINER_BYTES, output.length - pointer);
        for (int each = 0; each < room; each++) {
            output[pointer + each] = (byte) (container >>> (each * 8));
        }
    }
}
