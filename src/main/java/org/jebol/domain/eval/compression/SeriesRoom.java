package org.jebol.domain.eval.compression;

import org.jebol.domain.value.EvaluationFailure;
import org.jebol.domain.value.Raised;

public final class SeriesRoom {

    private static final long LARGEST_SERIES = Integer.MAX_VALUE;
    private static final int TINY_POOL = 8;
    private static final int VALUE_SIZE = 32;
    private static final int LAST_TINY_POOL = 16 * VALUE_SIZE;
    private static final int SMALL_POOL_STEP = 4 * VALUE_SIZE;
    private static final int LAST_SMALL_POOL = 30 * VALUE_SIZE;
    private static final int KILOBYTE = 1024;

    private SeriesRoom() {
    }

    public static long makeBinaryOf(long length) {
        long wanted = length + 1;
        if (wanted > LARGEST_SERIES) {
            throw Raised.of(EvaluationFailure.NO_MEMORY);
        }
        if (wanted <= TINY_POOL) {
            return TINY_POOL;
        }
        if (wanted <= LAST_TINY_POOL) {
            return roundUp(wanted, VALUE_SIZE);
        }
        if (wanted <= LAST_SMALL_POOL) {
            return Math.min(roundUp(wanted, SMALL_POOL_STEP), LAST_SMALL_POOL);
        }
        return roundUp(wanted, KILOBYTE);
    }

    private static long roundUp(long length, int step) {
        return (length + step - 1) / step * step;
    }
}
