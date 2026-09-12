package org.jebol.domain.eval.brotli;

final class BrotliMatch {

    private static final int WORTH_PER_LITERAL_BYTE = 135;
    private static final int COST_PER_DISTANCE_BIT = 30;

    private static final long FLOOR_KEEPING_EVERY_SCORE_POSITIVE = COST_PER_DISTANCE_BIT * 8L * 8L;

    long length;
    long distance;
    long score;

    int lengthCodeDelta;

    void forget(long lowestScoreWorthHaving) {
        length = 0;
        distance = 0;
        score = lowestScoreWorthHaving;
        lengthCodeDelta = 0;
    }

    static long scoreFor(long copyLength, long distance) {
        return FLOOR_KEEPING_EVERY_SCORE_POSITIVE + WORTH_PER_LITERAL_BYTE * copyLength
                - COST_PER_DISTANCE_BIT * (long) BrotliCodes.log2Floor((int) distance);
    }

    static long scoreUsingLastDistance(long copyLength) {
        return WORTH_PER_LITERAL_BYTE * copyLength + FLOOR_KEEPING_EVERY_SCORE_POSITIVE + 15;
    }

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
