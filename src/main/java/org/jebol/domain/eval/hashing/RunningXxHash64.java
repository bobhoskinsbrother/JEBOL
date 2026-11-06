package org.jebol.domain.eval.hashing;

public final class RunningXxHash64 extends XxHashFamily {

    private static final int STRIPE = 32;

    private final byte[] pending = new byte[STRIPE];
    private int pendingLength;
    private long totalLength;
    private long first;
    private long second;
    private long third;
    private long fourth;

    public RunningXxHash64() {
        reset();
    }

    public void reset() {
        first = P64_1 + P64_2;
        second = P64_2;
        third = 0;
        fourth = -P64_1;
        pendingLength = 0;
        totalLength = 0;
    }

    public void update(byte[] message, int from, int length) {
        totalLength += length;
        int at = from;
        int end = from + length;
        if (pendingLength + length < STRIPE) {
            System.arraycopy(message, at, pending, pendingLength, length);
            pendingLength += length;
            return;
        }
        if (pendingLength > 0) {
            int topUp = STRIPE - pendingLength;
            System.arraycopy(message, at, pending, pendingLength, topUp);
            consumeStripe(pending, 0);
            at += topUp;
            pendingLength = 0;
        }
        while (at + STRIPE <= end) {
            consumeStripe(message, at);
            at += STRIPE;
        }
        pendingLength = end - at;
        System.arraycopy(message, at, pending, 0, pendingLength);
    }

    public long digest() {
        long running;
        if (totalLength >= STRIPE) {
            running = Long.rotateLeft(first, 1) + Long.rotateLeft(second, 7)
                    + Long.rotateLeft(third, 12) + Long.rotateLeft(fourth, 18);
            running = foldInAfterOneMoreStir(running, first);
            running = foldInAfterOneMoreStir(running, second);
            running = foldInAfterOneMoreStir(running, third);
            running = foldInAfterOneMoreStir(running, fourth);
        } else {
            running = third + P64_5;
        }
        running += totalLength;
        int at = 0;
        while (at + 8 <= pendingLength) {
            running = Long.rotateLeft(
                    running ^ stir64(0, octets.readLittleEndianLongAt(pending, at)), 27) * P64_1 + P64_4;
            at += 8;
        }
        if (at + 4 <= pendingLength) {
            running = Long.rotateLeft(
                    running ^ (octets.readLittleEndianWordAt(pending, at) & 0xFFFFFFFFL) * P64_1, 23)
                    * P64_2 + P64_3;
            at += 4;
        }
        while (at < pendingLength) {
            running = Long.rotateLeft(running ^ (pending[at] & 0xFFL) * P64_5, 11) * P64_1;
            at++;
        }
        return avalanche64(running);
    }

    private void consumeStripe(byte[] message, int at) {
        first = stir64(first, octets.readLittleEndianLongAt(message, at));
        second = stir64(second, octets.readLittleEndianLongAt(message, at + 8));
        third = stir64(third, octets.readLittleEndianLongAt(message, at + 16));
        fourth = stir64(fourth, octets.readLittleEndianLongAt(message, at + 24));
    }

    private long stir64(long accumulator, long word) {
        return Long.rotateLeft(accumulator + word * P64_2, 31) * P64_1;
    }

    private long foldInAfterOneMoreStir(long running, long accumulator) {
        return (running ^ stir64(0, accumulator)) * P64_1 + P64_4;
    }
}
