package org.jebol.domain.eval.brotli;

import java.util.Arrays;

/**
 * The window of input the encoder can still refer back to.
 *
 * <p>{@code ringbuffer.h}. Writing past the end wraps to the beginning, and a
 * copy of the first block is kept after the end so that a match found near the
 * wrap can be read straight through without the reader minding the join.
 *
 * <p>Seven spare bytes past everything, kept at zero, because the hash of a
 * position reads eight bytes and the last few positions have fewer than eight
 * left. Without them the answer would depend on whatever happened to be in
 * memory.
 *
 * <p>The C also keeps a copy of the last two bytes just before the start. It is
 * written and never read, so it is not here.
 */
final class BrotliRingBuffer {

    private static final int SLACK_FOR_HASHING_PAST_THE_END = 7;

    private final int size;
    private final int mask;
    private final int tailSize;
    private final int totalSize;

    private byte[] data = new byte[0];
    private int howMuchIsAllocated;
    private int at;

    BrotliRingBuffer(int windowBits, int blockBits) {
        this.size = 1 << windowBits;
        this.mask = size - 1;
        this.tailSize = 1 << blockBits;
        this.totalSize = size + tailSize;
    }

    byte[] data() {
        return data;
    }

    int mask() {
        return mask;
    }

    private void allocate(int howMuch) {
        byte[] grown = new byte[howMuch + SLACK_FOR_HASHING_PAST_THE_END];
        System.arraycopy(data, 0, grown, 0,
                Math.min(data.length, howMuchIsAllocated));
        data = grown;
        howMuchIsAllocated = howMuch;
    }

    void write(byte[] source, int from, int howMany) {
        if (at == 0 && howMany < tailSize) {
            at = howMany;
            allocate(howMany);
            System.arraycopy(source, from, data, 0, howMany);
            return;
        }
        if (howMuchIsAllocated < totalSize) {
            allocate(totalSize);
            data[size - 2] = 0;
            data[size - 1] = 0;
            data[size] = (byte) 241;
        }
        int writingAt = at & mask;
        copyIntoTheTail(source, from, howMany, writingAt);
        if (writingAt + howMany <= size) {
            System.arraycopy(source, from, data, writingAt, howMany);
        } else {
            int upToTheEnd = Math.min(howMany, totalSize - writingAt);
            System.arraycopy(source, from, data, writingAt, upToTheEnd);
            System.arraycopy(source, from + size - writingAt, data, 0,
                    howMany - (size - writingAt));
        }
        at += howMany;
    }

    /**
     * Keeps the copy of the beginning that sits after the end up to date, so a
     * match that runs over the wrap reads as one run of bytes.
     */
    private void copyIntoTheTail(byte[] source, int from, int howMany,
            int writingAt) {

        if (writingAt >= tailSize) {
            return;
        }
        System.arraycopy(source, from, data, size + writingAt,
                Math.min(howMany, tailSize - writingAt));
    }

    /**
     * Zeroes the bytes just past what was written, but only on the first lap.
     *
     * <p>After that the tail already holds a copy of real data there, and
     * clearing it would destroy what a wrapped match reads.
     */
    void clearWhatTheHashesWouldReadPastTheEnd() {
        if (at > mask) {
            return;
        }
        Arrays.fill(data, at, at + SLACK_FOR_HASHING_PAST_THE_END, (byte) 0);
    }
}
