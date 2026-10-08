package org.jebol.domain.eval.compression.deflate;

import java.util.Arrays;

final class BlockSplitStatistics {

    static final int OBSERVATION_TYPES = 10;

    private static final int LITERAL_OBSERVATION_TYPES = 8;

    private static final int OBSERVATIONS_PER_BLOCK_CHECK = 512;

    private static final int FIRST_LONG_MATCH = 9;

    private static final long UNSIGNED_32 = 0xFFFFFFFFL;

    private static final int SHORT_BLOCK = 10000;

    private static final int FEW_ITEMS = 8192;

    private static final int LENGTH_PENALTY_STEP = 4096;

    final int[] newObservations = new int[OBSERVATION_TYPES];

    final int[] observations = new int[OBSERVATION_TYPES];

    int newObservationCount;

    int observationCount;

    void reset() {
        Arrays.fill(newObservations, 0);
        Arrays.fill(observations, 0);
        newObservationCount = 0;
        observationCount = 0;
    }

    void clearTheOldObservations() {
        Arrays.fill(observations, 0);
        observationCount = 0;
    }

    void observeLiteral(int literal) {
        newObservations[((literal >> 5) & 0x6) | (literal & 1)]++;
        newObservationCount++;
    }

    void observeMatch(int length) {
        newObservations[LITERAL_OBSERVATION_TYPES + (length >= FIRST_LONG_MATCH ? 1 : 0)]++;
        newObservationCount++;
    }

    void mergeNewObservations() {
        for (int type = 0; type < OBSERVATION_TYPES; type++) {
            observations[type] += newObservations[type];
            newObservations[type] = 0;
        }
        observationCount += newObservationCount;
        newObservationCount = 0;
    }

    boolean readyToCheck(int blockBegin, int next, int end) {
        return newObservationCount >= OBSERVATIONS_PER_BLOCK_CHECK
                && next - blockBegin >= DeflateTables.MIN_BLOCK_LENGTH
                && end - next >= DeflateTables.MIN_BLOCK_LENGTH;
    }

    boolean shouldEndBlock(int blockBegin, int next, int end) {
        return readyToCheck(blockBegin, next, end) && endsTheBlock(next - blockBegin);
    }

    boolean endsTheBlock(int blockLength) {
        if (observationCount > 0) {
            long totalDelta = 0;
            for (int type = 0; type < OBSERVATION_TYPES; type++) {
                long expected = unsigned((long) observations[type] * newObservationCount);
                long actual = unsigned((long) newObservations[type] * observationCount);
                totalDelta = unsigned(totalDelta + Math.abs(actual - expected));
            }
            long items = unsigned((long) observationCount + newObservationCount);
            long cutoff = unsigned(unsigned((long) newObservationCount * 200) / 512 * observationCount);
            if (blockLength < SHORT_BLOCK && items < FEW_ITEMS) {
                cutoff = unsigned(cutoff + cutoff * (FEW_ITEMS - items) / FEW_ITEMS);
            }
            if (unsigned(totalDelta + unsigned((long) (blockLength / LENGTH_PENALTY_STEP) * observationCount))
                    >= cutoff) {
                return true;
            }
        }
        mergeNewObservations();
        return false;
    }

    private long unsigned(long value) {
        return value & UNSIGNED_32;
    }
}
