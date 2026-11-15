package org.jebol.domain.eval.compression.zstd;

final class ZstdBlockSplitting {

    private static final int THRESHOLD_PENALTY_RATE = 16;
    private static final int THRESHOLD_BASE = THRESHOLD_PENALTY_RATE - 2;
    private static final int THRESHOLD_PENALTY = 3;
    private static final int HASH_TABLE_SIZE = 1 << 10;
    private static final int KNUTH = 0x9e3779b9;
    private static final int CHUNK_SIZE = 8 << 10;
    private static final int SEGMENT_SIZE = 512;
    private static final int[] SAMPLING_RATES = {43, 11, 5, 1};
    private static final int[] HASH_LOGS = {8, 9, 10, 10};

    private ZstdBlockSplitting() {
    }

    private static final class Fingerprint {
        final long[] events = new long[HASH_TABLE_SIZE];
        long eventCount;
    }

    private static int hash2(byte[] source, int at, int hashLog) {
        if (hashLog == 8) {
            return source[at] & 0xFF;
        }
        return (ZstdOctets.readShort(source, at) * KNUTH) >>> (32 - hashLog);
    }

    private static void record(Fingerprint fingerprint, byte[] source, int from, int size, int rate, int hashLog) {
        java.util.Arrays.fill(fingerprint.events, 0, 1 << hashLog, 0);
        fingerprint.eventCount = 0;
        int limit = size - 2 + 1;
        for (int at = 0; at < limit; at += rate) {
            fingerprint.events[hash2(source, from + at, hashLog)]++;
        }
        fingerprint.eventCount += limit / rate;
    }

    private static long distance(Fingerprint first, Fingerprint second, int hashLog) {
        long total = 0;
        for (int at = 0; at < 1 << hashLog; at++) {
            total += Math.abs(first.events[at] * second.eventCount - second.events[at] * first.eventCount);
        }
        return total;
    }

    private static boolean isDifferent(Fingerprint reference, Fingerprint fresh, int penalty, int hashLog) {
        long p50 = reference.eventCount * fresh.eventCount;
        long deviation = distance(reference, fresh, hashLog);
        long threshold = Long.divideUnsigned(p50 * (THRESHOLD_BASE + penalty), THRESHOLD_PENALTY_RATE);
        return Long.compareUnsigned(deviation, threshold) >= 0;
    }

    private static int splitByChunks(byte[] source, int from, int blockSize, int level) {
        Fingerprint past = new Fingerprint();
        Fingerprint fresh = new Fingerprint();
        int rate = SAMPLING_RATES[level];
        int hashLog = HASH_LOGS[level];
        int penalty = THRESHOLD_PENALTY;
        record(past, source, from, CHUNK_SIZE, rate, hashLog);
        for (int position = CHUNK_SIZE; position <= blockSize - CHUNK_SIZE; position += CHUNK_SIZE) {
            record(fresh, source, from + position, CHUNK_SIZE, rate, hashLog);
            if (isDifferent(past, fresh, penalty, hashLog)) {
                return position;
            }
            for (int at = 0; at < HASH_TABLE_SIZE; at++) {
                past.events[at] += fresh.events[at];
            }
            past.eventCount += fresh.eventCount;
            if (penalty > 0) {
                penalty--;
            }
        }
        return blockSize;
    }

    private static void addHistogram(long[] events, byte[] source, int from, int size) {
        for (int at = from; at < from + size; at++) {
            events[source[at] & 0xFF]++;
        }
    }

    private static int splitFromBorders(byte[] source, int from, int blockSize) {
        Fingerprint past = new Fingerprint();
        Fingerprint fresh = new Fingerprint();
        Fingerprint middle = new Fingerprint();
        addHistogram(past.events, source, from, SEGMENT_SIZE);
        addHistogram(fresh.events, source, from + blockSize - SEGMENT_SIZE, SEGMENT_SIZE);
        past.eventCount = SEGMENT_SIZE;
        fresh.eventCount = SEGMENT_SIZE;
        if (!isDifferent(past, fresh, 0, 8)) {
            return blockSize;
        }
        addHistogram(middle.events, source, from + blockSize / 2 - SEGMENT_SIZE / 2, SEGMENT_SIZE);
        middle.eventCount = SEGMENT_SIZE;
        long fromBegin = distance(past, middle, 8);
        long fromEnd = distance(fresh, middle, 8);
        long minDistance = SEGMENT_SIZE * SEGMENT_SIZE / 3;
        if (Math.abs(fromBegin - fromEnd) < minDistance) {
            return 64 * 1024;
        }
        return Long.compareUnsigned(fromBegin, fromEnd) > 0 ? 32 * 1024 : 96 * 1024;
    }

    static int splitBlock(byte[] source, int from, int blockSize, int level) {
        if (level == 0) {
            return splitFromBorders(source, from, blockSize);
        }
        return splitByChunks(source, from, blockSize, level - 1);
    }
}
