package org.jebol.domain.eval.compression.zstd;

final class ZstdCompressionParameters {

    static final int FAST = 1;
    static final int DFAST = 2;
    static final int GREEDY = 3;
    static final int LAZY = 4;
    static final int LAZY2 = 5;
    static final int BTLAZY2 = 6;
    static final int BTOPT = 7;
    static final int BTULTRA = 8;
    static final int BTULTRA2 = 9;

    static final int MAX_LEVEL = 22;
    static final int DEFAULT_LEVEL = 3;
    static final int MIN_LEVEL = -(1 << 17);
    static final long CONTENT_SIZE_UNKNOWN = -1L;

    static final int WINDOW_LOG_MAX = 31;
    static final int WINDOW_LOG_MIN = 10;
    static final int HASH_LOG_MIN = 6;
    static final int HASH_LOG_MAX = 30;
    static final int CHAIN_LOG_MIN = 6;
    static final int CHAIN_LOG_MAX = 30;
    static final int SEARCH_LOG_MIN = 1;
    static final int SEARCH_LOG_MAX = WINDOW_LOG_MAX - 1;
    static final int MIN_MATCH_MIN = 3;
    static final int MIN_MATCH_MAX = 7;
    static final int TARGET_LENGTH_MAX = 1 << 17;
    static final int ROW_HASH_TAG_BITS = 8;

    private static final int[][][] DEFAULT_PARAMETERS = {
            {
                    {19, 12, 13, 1, 6, 1, FAST}, {19, 13, 14, 1, 7, 0, FAST}, {20, 15, 16, 1, 6, 0, FAST},
                    {21, 16, 17, 1, 5, 0, DFAST}, {21, 18, 18, 1, 5, 0, DFAST}, {21, 18, 19, 3, 5, 2, GREEDY},
                    {21, 18, 19, 3, 5, 4, LAZY}, {21, 19, 20, 4, 5, 8, LAZY}, {21, 19, 20, 4, 5, 16, LAZY2},
                    {22, 20, 21, 4, 5, 16, LAZY2}, {22, 21, 22, 5, 5, 16, LAZY2}, {22, 21, 22, 6, 5, 16, LAZY2},
                    {22, 22, 23, 6, 5, 32, LAZY2}, {22, 22, 22, 4, 5, 32, BTLAZY2}, {22, 22, 23, 5, 5, 32, BTLAZY2},
                    {22, 23, 23, 6, 5, 32, BTLAZY2}, {22, 22, 22, 5, 5, 48, BTOPT}, {23, 23, 22, 5, 4, 64, BTOPT},
                    {23, 23, 22, 6, 3, 64, BTULTRA}, {23, 24, 22, 7, 3, 256, BTULTRA2},
                    {25, 25, 23, 7, 3, 256, BTULTRA2}, {26, 26, 24, 7, 3, 512, BTULTRA2},
                    {27, 27, 25, 9, 3, 999, BTULTRA2}},
            {
                    {18, 12, 13, 1, 5, 1, FAST}, {18, 13, 14, 1, 6, 0, FAST}, {18, 14, 14, 1, 5, 0, DFAST},
                    {18, 16, 16, 1, 4, 0, DFAST}, {18, 16, 17, 3, 5, 2, GREEDY}, {18, 17, 18, 5, 5, 2, GREEDY},
                    {18, 18, 19, 3, 5, 4, LAZY}, {18, 18, 19, 4, 4, 4, LAZY}, {18, 18, 19, 4, 4, 8, LAZY2},
                    {18, 18, 19, 5, 4, 8, LAZY2}, {18, 18, 19, 6, 4, 8, LAZY2}, {18, 18, 19, 5, 4, 12, BTLAZY2},
                    {18, 19, 19, 7, 4, 12, BTLAZY2}, {18, 18, 19, 4, 4, 16, BTOPT}, {18, 18, 19, 4, 3, 32, BTOPT},
                    {18, 18, 19, 6, 3, 128, BTOPT}, {18, 19, 19, 6, 3, 128, BTULTRA},
                    {18, 19, 19, 8, 3, 256, BTULTRA}, {18, 19, 19, 6, 3, 128, BTULTRA2},
                    {18, 19, 19, 8, 3, 256, BTULTRA2}, {18, 19, 19, 10, 3, 512, BTULTRA2},
                    {18, 19, 19, 12, 3, 512, BTULTRA2}, {18, 19, 19, 13, 3, 999, BTULTRA2}},
            {
                    {17, 12, 12, 1, 5, 1, FAST}, {17, 12, 13, 1, 6, 0, FAST}, {17, 13, 15, 1, 5, 0, FAST},
                    {17, 15, 16, 2, 5, 0, DFAST}, {17, 17, 17, 2, 4, 0, DFAST}, {17, 16, 17, 3, 4, 2, GREEDY},
                    {17, 16, 17, 3, 4, 4, LAZY}, {17, 16, 17, 3, 4, 8, LAZY2}, {17, 16, 17, 4, 4, 8, LAZY2},
                    {17, 16, 17, 5, 4, 8, LAZY2}, {17, 16, 17, 6, 4, 8, LAZY2}, {17, 17, 17, 5, 4, 8, BTLAZY2},
                    {17, 18, 17, 7, 4, 12, BTLAZY2}, {17, 18, 17, 3, 4, 12, BTOPT}, {17, 18, 17, 4, 3, 32, BTOPT},
                    {17, 18, 17, 6, 3, 256, BTOPT}, {17, 18, 17, 6, 3, 128, BTULTRA},
                    {17, 18, 17, 8, 3, 256, BTULTRA}, {17, 18, 17, 10, 3, 512, BTULTRA},
                    {17, 18, 17, 5, 3, 256, BTULTRA2}, {17, 18, 17, 7, 3, 512, BTULTRA2},
                    {17, 18, 17, 9, 3, 512, BTULTRA2}, {17, 18, 17, 11, 3, 999, BTULTRA2}},
            {
                    {14, 12, 13, 1, 5, 1, FAST}, {14, 14, 15, 1, 5, 0, FAST}, {14, 14, 15, 1, 4, 0, FAST},
                    {14, 14, 15, 2, 4, 0, DFAST}, {14, 14, 14, 4, 4, 2, GREEDY}, {14, 14, 14, 3, 4, 4, LAZY},
                    {14, 14, 14, 4, 4, 8, LAZY2}, {14, 14, 14, 6, 4, 8, LAZY2}, {14, 14, 14, 8, 4, 8, LAZY2},
                    {14, 15, 14, 5, 4, 8, BTLAZY2}, {14, 15, 14, 9, 4, 8, BTLAZY2}, {14, 15, 14, 3, 4, 12, BTOPT},
                    {14, 15, 14, 4, 3, 24, BTOPT}, {14, 15, 14, 5, 3, 32, BTULTRA},
                    {14, 15, 15, 6, 3, 64, BTULTRA}, {14, 15, 15, 7, 3, 256, BTULTRA},
                    {14, 15, 15, 5, 3, 48, BTULTRA2}, {14, 15, 15, 6, 3, 128, BTULTRA2},
                    {14, 15, 15, 7, 3, 256, BTULTRA2}, {14, 15, 15, 8, 3, 256, BTULTRA2},
                    {14, 15, 15, 8, 3, 512, BTULTRA2}, {14, 15, 15, 9, 3, 512, BTULTRA2},
                    {14, 15, 15, 10, 3, 999, BTULTRA2}}};

    int windowLog;
    int chainLog;
    int hashLog;
    int searchLog;
    int minMatch;
    int targetLength;
    int strategy;

    ZstdCompressionParameters copy() {
        ZstdCompressionParameters copy = new ZstdCompressionParameters();
        copy.windowLog = windowLog;
        copy.chainLog = chainLog;
        copy.hashLog = hashLog;
        copy.searchLog = searchLog;
        copy.minMatch = minMatch;
        copy.targetLength = targetLength;
        copy.strategy = strategy;
        return copy;
    }

    boolean isSameAs(ZstdCompressionParameters other) {
        return windowLog == other.windowLog && chainLog == other.chainLog && hashLog == other.hashLog
                && searchLog == other.searchLog && minMatch == other.minMatch
                && targetLength == other.targetLength && strategy == other.strategy;
    }

    static boolean isRowMatchFinderSupported(int strategy) {
        return strategy >= GREEDY && strategy <= LAZY2;
    }

    static int cycleLog(int hashLog, int strategy) {
        return hashLog - (strategy >= BTLAZY2 ? 1 : 0);
    }

    private static int dictionaryAndWindowLog(int windowLog) {
        return windowLog;
    }

    void adjust(long sourceSize, boolean isRowMatchFinderUsedIfSupported) {
        long maxWindowResize = 1L << (WINDOW_LOG_MAX - 1);
        if (Long.compareUnsigned(sourceSize, maxWindowResize) <= 0) {
            int total = (int) sourceSize;
            int hashSizeMin = 1 << HASH_LOG_MIN;
            int sourceLog = Integer.compareUnsigned(total, hashSizeMin) < 0 ? HASH_LOG_MIN
                    : ZstdOctets.highBit(total - 1) + 1;
            if (windowLog > sourceLog) {
                windowLog = sourceLog;
            }
        }
        if (sourceSize != CONTENT_SIZE_UNKNOWN) {
            int windowAndDictionaryLog = dictionaryAndWindowLog(windowLog);
            int cycle = cycleLog(chainLog, strategy);
            if (hashLog > windowAndDictionaryLog + 1) {
                hashLog = windowAndDictionaryLog + 1;
            }
            if (cycle > windowAndDictionaryLog) {
                chainLog -= cycle - windowAndDictionaryLog;
            }
        }
        if (windowLog < WINDOW_LOG_MIN) {
            windowLog = WINDOW_LOG_MIN;
        }
        if (isRowMatchFinderUsedIfSupported && isRowMatchFinderSupported(strategy)) {
            int rowLog = Math.max(4, Math.min(searchLog, 6));
            int maxRowHashLog = 32 - ROW_HASH_TAG_BITS;
            int maxHashLog = maxRowHashLog + rowLog;
            if (hashLog > maxHashLog) {
                hashLog = maxHashLog;
            }
        }
    }

    static ZstdCompressionParameters forLevel(int level, long sourceSizeHint) {
        long rowSize = sourceSizeHint;
        int table = (Long.compareUnsigned(rowSize, 256 * 1024) <= 0 ? 1 : 0)
                + (Long.compareUnsigned(rowSize, 128 * 1024) <= 0 ? 1 : 0)
                + (Long.compareUnsigned(rowSize, 16 * 1024) <= 0 ? 1 : 0);
        int row;
        if (level == 0) {
            row = DEFAULT_LEVEL;
        } else if (level < 0) {
            row = 0;
        } else {
            row = Math.min(level, MAX_LEVEL);
        }
        int[] chosen = DEFAULT_PARAMETERS[table][row];
        ZstdCompressionParameters parameters = new ZstdCompressionParameters();
        parameters.windowLog = chosen[0];
        parameters.chainLog = chosen[1];
        parameters.hashLog = chosen[2];
        parameters.searchLog = chosen[3];
        parameters.minMatch = chosen[4];
        parameters.targetLength = chosen[5];
        parameters.strategy = chosen[6];
        if (level < 0) {
            int clamped = Math.max(MIN_LEVEL, level);
            parameters.targetLength = -clamped;
        }
        parameters.adjust(sourceSizeHint, true);
        return parameters;
    }
}
