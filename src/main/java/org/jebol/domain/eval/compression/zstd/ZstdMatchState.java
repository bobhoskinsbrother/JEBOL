package org.jebol.domain.eval.compression.zstd;

final class ZstdMatchState {

    static final int WINDOW_START_INDEX = 2;
    static final int HASH_READ_SIZE = 8;
    static final long CURRENT_MAX = 3500L * 1024 * 1024;
    static final int SEARCH_STRENGTH = 8;

    private static final byte[] NOWHERE = {' ', 0};
    private static final int PRIME_3_BYTES = 506832829;
    private static final int PRIME_4_BYTES = (int) 2654435761L;
    private static final long PRIME_5_BYTES = 889523592379L;
    private static final long PRIME_6_BYTES = 227718039650203L;
    private static final long PRIME_7_BYTES = 58295818150454627L;
    private static final long PRIME_8_BYTES = 0xCF1BBCDCB7A56463L;

    byte[] baseArray = NOWHERE;
    long base;
    byte[] dictionaryArray = NOWHERE;
    long dictionaryBase;
    long dictionaryLimit;
    long lowLimit;
    byte[] nextSourceArray = NOWHERE;
    long nextSource;
    int overflowCorrections;

    long loadedDictionaryEnd;
    long nextToUpdate;
    int hashLog3;
    int rowHashLog;
    byte[] tagTable;
    final int[] hashCache = new int[8];
    long hashSalt;
    int hashSaltEntropy;
    ZstdIndexTable hashTable;
    ZstdIndexTable hashTable3;
    ZstdIndexTable chainTable;
    ZstdCompressionParameters parameters;
    boolean isLazySkipping;
    final ZstdOptimalState optimal = new ZstdOptimalState();
    ZstdRawSequenceStore longDistanceSequences;

    void initWindow() {
        baseArray = NOWHERE;
        base = 0;
        dictionaryArray = NOWHERE;
        dictionaryBase = 0;
        dictionaryLimit = WINDOW_START_INDEX;
        lowLimit = WINDOW_START_INDEX;
        nextSourceArray = NOWHERE;
        nextSource = WINDOW_START_INDEX;
        overflowCorrections = 0;
    }

    void clearWindow() {
        long end = nextSource - base;
        lowLimit = end & 0xFFFFFFFFL;
        dictionaryLimit = end & 0xFFFFFFFFL;
    }

    boolean hasExtendedDictionary() {
        return lowLimit < dictionaryLimit;
    }

    boolean updateWindow(byte[] source, int from, int size, boolean isForcedNonContiguous) {
        boolean isContiguous = true;
        if (size == 0) {
            return true;
        }
        if (source != nextSourceArray || from != nextSource || isForcedNonContiguous) {
            long distanceFromBase = nextSource - base;
            lowLimit = dictionaryLimit;
            dictionaryLimit = distanceFromBase & 0xFFFFFFFFL;
            dictionaryArray = baseArray;
            dictionaryBase = base;
            baseArray = source;
            base = from - distanceFromBase;
            if (dictionaryLimit - lowLimit < HASH_READ_SIZE) {
                lowLimit = dictionaryLimit;
            }
            isContiguous = false;
        }
        nextSourceArray = source;
        nextSource = (long) from + size;
        if (source == dictionaryArray && (long) from + size > dictionaryBase + lowLimit
                && from < dictionaryBase + dictionaryLimit) {
            long highInputIndex = (long) from + size - dictionaryBase;
            lowLimit = highInputIndex > dictionaryLimit ? dictionaryLimit : highInputIndex & 0xFFFFFFFFL;
        }
        return isContiguous;
    }

    long indexOf(int position) {
        return position - base;
    }

    int positionOf(long index) {
        return (int) (base + index);
    }

    int dictionaryPositionOf(long index) {
        return (int) (dictionaryBase + index);
    }

    long lowestMatchIndex(long current, int windowLog) {
        long maxDistance = 1L << windowLog;
        long lowestValid = lowLimit;
        long withinWindow = ((current - lowestValid) & 0xFFFFFFFFL) > maxDistance ? current - maxDistance : lowestValid;
        return loadedDictionaryEnd != 0 ? lowestValid : withinWindow & 0xFFFFFFFFL;
    }

    long lowestPrefixIndex(long current, int windowLog) {
        long maxDistance = 1L << windowLog;
        long lowestValid = dictionaryLimit;
        long withinWindow = ((current - lowestValid) & 0xFFFFFFFFL) > maxDistance ? current - maxDistance : lowestValid;
        return loadedDictionaryEnd != 0 ? lowestValid : withinWindow & 0xFFFFFFFFL;
    }

    static boolean isOutsideOverlap(long prefixLowestIndex, long repeatIndex) {
        return ((prefixLowestIndex - 1 - repeatIndex) & 0xFFFFFFFFL) >= 3;
    }

    static int read32(byte[] buffer, int at) {
        return ZstdOctets.readInt(buffer, at);
    }

    static long read64(byte[] buffer, int at) {
        return ZstdOctets.readLong(buffer, at);
    }

    static int hash3(int value, int bits, int salt) {
        return (((value << 8) * PRIME_3_BYTES) ^ salt) >>> (32 - bits);
    }

    static int hash3At(byte[] buffer, int at, int bits) {
        return hash3(read32(buffer, at), bits, 0);
    }

    static int hashAt(byte[] buffer, int at, int bits, int minMatch) {
        return hashSaltedAt(buffer, at, bits, minMatch, 0);
    }

    static int hashSaltedAt(byte[] buffer, int at, int bits, int minMatch, long salt) {
        return switch (minMatch) {
            case 5 -> (int) ((((read64(buffer, at) << 24) * PRIME_5_BYTES) ^ salt) >>> (64 - bits));
            case 6 -> (int) ((((read64(buffer, at) << 16) * PRIME_6_BYTES) ^ salt) >>> (64 - bits));
            case 7 -> (int) ((((read64(buffer, at) << 8) * PRIME_7_BYTES) ^ salt) >>> (64 - bits));
            case 8 -> (int) (((read64(buffer, at) * PRIME_8_BYTES) ^ salt) >>> (64 - bits));
            default -> ((read32(buffer, at) * PRIME_4_BYTES) ^ (int) salt) >>> (32 - bits);
        };
    }

    static int hash8(long value, int bits) {
        return (int) ((value * PRIME_8_BYTES) >>> (64 - bits));
    }

    static int count(byte[] buffer, int input, int match, int inputLimit) {
        int start = input;
        int in = input;
        int at = match;
        int loopLimit = inputLimit - 7;
        if (in < loopLimit) {
            long difference = read64(buffer, at) ^ read64(buffer, in);
            if (difference != 0) {
                return Long.numberOfTrailingZeros(difference) >>> 3;
            }
            in += 8;
            at += 8;
            while (in < loopLimit) {
                difference = read64(buffer, at) ^ read64(buffer, in);
                if (difference == 0) {
                    in += 8;
                    at += 8;
                    continue;
                }
                in += Long.numberOfTrailingZeros(difference) >>> 3;
                return in - start;
            }
        }
        if (in < inputLimit - 3 && read32(buffer, at) == read32(buffer, in)) {
            in += 4;
            at += 4;
        }
        if (in < inputLimit - 1 && ZstdOctets.readShort(buffer, at) == ZstdOctets.readShort(buffer, in)) {
            in += 2;
            at += 2;
        }
        if (in < inputLimit && buffer[at] == buffer[in]) {
            in++;
        }
        return in - start;
    }

    static int countAcross(byte[] inputArray, int input, byte[] matchArray, int match, int inputLimit) {
        if (inputArray == matchArray) {
            return count(inputArray, input, match, inputLimit);
        }
        int start = input;
        int in = input;
        int at = match;
        int loopLimit = inputLimit - 7;
        if (in < loopLimit) {
            long difference = read64(matchArray, at) ^ read64(inputArray, in);
            if (difference != 0) {
                return Long.numberOfTrailingZeros(difference) >>> 3;
            }
            in += 8;
            at += 8;
            while (in < loopLimit) {
                difference = read64(matchArray, at) ^ read64(inputArray, in);
                if (difference == 0) {
                    in += 8;
                    at += 8;
                    continue;
                }
                in += Long.numberOfTrailingZeros(difference) >>> 3;
                return in - start;
            }
        }
        if (in < inputLimit - 3 && read32(matchArray, at) == read32(inputArray, in)) {
            in += 4;
            at += 4;
        }
        if (in < inputLimit - 1 && ZstdOctets.readShort(matchArray, at) == ZstdOctets.readShort(inputArray, in)) {
            in += 2;
            at += 2;
        }
        if (in < inputLimit && matchArray[at] == inputArray[in]) {
            in++;
        }
        return in - start;
    }

    static int countTwoSegments(byte[] inputArray, int input, byte[] matchArray, int match, int inputEnd,
                                int matchEnd, int inputStart) {
        int virtualEnd = (int) Math.min((long) input + (matchEnd - match), inputEnd);
        int length = countAcross(inputArray, input, matchArray, match, virtualEnd);
        if (match + length != matchEnd) {
            return length;
        }
        return length + count(inputArray, input + length, inputStart, inputEnd);
    }
}
