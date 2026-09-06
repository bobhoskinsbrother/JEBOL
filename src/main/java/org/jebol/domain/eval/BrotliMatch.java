package org.jebol.domain.eval;

/**
 * The best backward reference found so far at one position.
 *
 * <p>{@code HasherSearchResult} in {@code hash.h}. Every hasher fills one of
 * these in and the caller reads it once, so it is mutable and reused rather
 * than allocated per position.
 *
 * <p>The score is not a bit count. It is a rough stand-in for one: a match is
 * worth a hundred and thirty-five per byte it saves, less thirty per bit needed
 * to name how far back it starts. That is what lets a nearer short match beat a
 * distant long one without pricing either properly.
 */
final class BrotliMatch {

    private static final int WORTH_PER_LITERAL_BYTE = 135;
    private static final int COST_PER_DISTANCE_BIT = 30;

    /** Enough that the largest distance penalty still leaves a positive score. */
    private static final long FLOOR = COST_PER_DISTANCE_BIT * 8L * 8L;

    long length;
    long distance;
    long score;

    /** How far the length written down differs from the length copied. */
    int lengthCodeDelta;

    void forget(long lowestScoreWorthHaving) {
        length = 0;
        distance = 0;
        score = lowestScoreWorthHaving;
        lengthCodeDelta = 0;
    }

    static long scoreFor(long copyLength, long distance) {
        return FLOOR + WORTH_PER_LITERAL_BYTE * copyLength
                - COST_PER_DISTANCE_BIT * (long) BrotliCodes.log2Floor((int) distance);
    }

    /**
     * A copy that reuses a recent distance costs no bits to place, so it is
     * scored as if the distance were free and then given a small bonus.
     */
    static long scoreUsingLastDistance(long copyLength) {
        return WORTH_PER_LITERAL_BYTE * copyLength + FLOOR + 15;
    }

    /**
     * What the second to sixteenth recent distances cost against the first.
     *
     * <p>The constant is the C's: nine penalties of two bits each, packed into
     * one number and shifted out by the code being priced.
     */
    static long penaltyForRecentDistance(int shortCode) {
        return 39 + ((0x1CA10 >> (shortCode & 0xE)) & 0xE);
    }

    static int matchingBytes(byte[] data, int firstAt, int secondAt, int limit) {
        int matched = 0;
        while (matched < limit && data[firstAt + matched] == data[secondAt + matched]) {
            matched++;
        }
        return matched;
    }
}
