package org.jebol.domain.eval.brotli;

import java.util.Arrays;

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
        copyIntoTheTailSoAWrappedMatchReadsAsOneRun(
                source, from, howMany, writingAt);
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

    private void copyIntoTheTailSoAWrappedMatchReadsAsOneRun(byte[] source, int from, int howMany,
            int writingAt) {

        if (writingAt >= tailSize) {
            return;
        }
        System.arraycopy(source, from, data, size + writingAt,
                Math.min(howMany, tailSize - writingAt));
    }

    void clearWhatTheHashesWouldReadPastTheEnd() {
        if (theFirstLapIsOverAndTheTailHoldsRealData()) {
            return;
        }
        Arrays.fill(data, at, at + SLACK_FOR_HASHING_PAST_THE_END, (byte) 0);
    }

    private boolean theFirstLapIsOverAndTheTailHoldsRealData() {
        return at > mask;
    }
}
