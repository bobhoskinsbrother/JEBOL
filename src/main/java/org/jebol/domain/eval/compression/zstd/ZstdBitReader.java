package org.jebol.domain.eval.compression.zstd;

final class ZstdBitReader {

    static final int UNFINISHED = 0;
    static final int END_OF_BUFFER = 1;
    static final int COMPLETED = 2;
    static final int OVERFLOW = 3;

    private static final int CONTAINER_BITS = 64;
    private static final int CONTAINER_BYTES = 8;
    private static final int PAST_ANY_BUFFER = Integer.MIN_VALUE;

    private byte[] source;
    private long container;
    private int consumed;
    private int pointer;
    private int start;
    private int limit;

    int consumed() {
        return consumed;
    }

    void forceConsumed(int bits) {
        consumed = bits;
    }

    int pointer() {
        return pointer;
    }

    void open(byte[] buffer, int from, int length) {
        if (length < 1) {
            source = buffer;
            container = 0;
            consumed = 0;
            pointer = 0;
            start = 0;
            limit = 0;
            throw ZstdError.SRC_SIZE_WRONG.failure();
        }
        source = buffer;
        start = from;
        limit = from + CONTAINER_BYTES;
        int lastByte = buffer[from + length - 1] & 0xFF;
        if (length >= CONTAINER_BYTES) {
            pointer = from + length - CONTAINER_BYTES;
            container = ZstdOctets.readLong(buffer, pointer);
            consumed = lastByte != 0 ? 8 - ZstdOctets.highBit(lastByte) : 0;
            if (lastByte == 0) {
                throw ZstdError.GENERIC.failure();
            }
        } else {
            pointer = from;
            long filled = buffer[from] & 0xFFL;
            for (int at = length - 1; at >= 1; at--) {
                filled += (buffer[from + at] & 0xFFL) << (at * 8);
            }
            container = filled;
            consumed = lastByte != 0 ? 8 - ZstdOctets.highBit(lastByte) : 0;
            if (lastByte == 0) {
                throw ZstdError.CORRUPTION_DETECTED.failure();
            }
            consumed += (CONTAINER_BYTES - length) * 8;
        }
    }

    void resume(byte[] buffer, long bits, int bitsConsumed, int lowest, int at) {
        source = buffer;
        container = bits;
        consumed = bitsConsumed;
        start = lowest;
        limit = lowest + CONTAINER_BYTES;
        pointer = at;
    }

    long look(int bits) {
        int from = (CONTAINER_BITS - consumed - bits) & (CONTAINER_BITS - 1);
        return (container >>> from) & ((1L << bits) - 1);
    }

    long lookFast(int bits) {
        return (container << (consumed & (CONTAINER_BITS - 1))) >>> ((CONTAINER_BITS - bits) & (CONTAINER_BITS - 1));
    }

    void skip(int bits) {
        consumed += bits;
    }

    long read(int bits) {
        long value = look(bits);
        skip(bits);
        return value;
    }

    long readFast(int bits) {
        long value = lookFast(bits);
        skip(bits);
        return value;
    }

    int reloadFast() {
        if (pointer < limit) {
            return OVERFLOW;
        }
        return reloadWhole();
    }

    int reload() {
        if (consumed > CONTAINER_BITS) {
            pointer = PAST_ANY_BUFFER;
            return OVERFLOW;
        }
        if (pointer >= limit) {
            return reloadWhole();
        }
        if (pointer == start) {
            return consumed < CONTAINER_BITS ? END_OF_BUFFER : COMPLETED;
        }
        int bytes = consumed >>> 3;
        int result = UNFINISHED;
        if (pointer - bytes < start) {
            bytes = pointer - start;
            result = END_OF_BUFFER;
        }
        pointer -= bytes;
        consumed -= bytes * 8;
        container = ZstdOctets.readLong(source, pointer);
        return result;
    }

    boolean isEndOfStream() {
        return pointer == start && consumed == CONTAINER_BITS;
    }

    private int reloadWhole() {
        pointer -= consumed >>> 3;
        consumed &= 7;
        container = ZstdOctets.readLong(source, pointer);
        return UNFINISHED;
    }
}
