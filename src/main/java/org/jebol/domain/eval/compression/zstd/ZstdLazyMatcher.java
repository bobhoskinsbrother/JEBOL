package org.jebol.domain.eval.compression.zstd;

final class ZstdLazyMatcher {

    static final int HASH_CHAIN = 0;
    static final int BINARY_TREE = 1;
    static final int ROW_HASH = 2;

    private static final int LAZY_SKIPPING_STEP = 8;
    private static final int ROW_HASH_CACHE_SIZE = 8;
    private static final int ROW_HASH_CACHE_MASK = ROW_HASH_CACHE_SIZE - 1;
    private static final int ROW_HASH_TAG_BITS = 8;
    private static final int ROW_HASH_TAG_MASK = (1 << ROW_HASH_TAG_BITS) - 1;
    private static final int ROW_HASH_MAX_ENTRIES = 64;
    private static final int UNSORTED_MARK = 1;
    private static final long NOT_FOUND = 999999999L;

    private final ZstdMatchState state;
    private final long[] offsetFound = new long[1];
    private final long[] matchBuffer = new long[ROW_HASH_MAX_ENTRIES];

    ZstdLazyMatcher(ZstdMatchState state) {
        this.state = state;
    }

    private static long mask32(long value) {
        return value & 0xFFFFFFFFL;
    }

    private static int highBit(long value) {
        return 63 - Long.numberOfLeadingZeros(value & 0xFFFFFFFFL);
    }

    private long insertAndFindFirstIndex(int input, int minMatch, boolean isLazySkipping) {
        ZstdIndexTable hashTable = state.hashTable;
        ZstdIndexTable chainTable = state.chainTable;
        int hashLog = state.parameters.hashLog;
        int chainMask = (1 << state.parameters.chainLog) - 1;
        byte[] source = state.baseArray;
        long target = state.indexOf(input);
        long index = state.nextToUpdate;
        while (index < target) {
            int hash = ZstdMatchState.hashAt(source, state.positionOf(index), hashLog, minMatch);
            chainTable.set((int) (index & chainMask), hashTable.get(hash));
            hashTable.setIndex(hash, index);
            index++;
            if (isLazySkipping) {
                break;
            }
        }
        state.nextToUpdate = target;
        return hashTable.getIndex(ZstdMatchState.hashAt(source, input, hashLog, minMatch));
    }

    private int hashChainFindBestMatch(int input, int inputLimit, long[] offsetBase, int minMatch,
                                       boolean isExtended) {
        ZstdCompressionParameters parameters = state.parameters;
        ZstdIndexTable chainTable = state.chainTable;
        long chainSize = 1L << parameters.chainLog;
        int chainMask = (int) chainSize - 1;
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        long dictionaryLimit = state.dictionaryLimit;
        int prefixStart = state.positionOf(dictionaryLimit);
        int dictionaryEnd = state.dictionaryPositionOf(dictionaryLimit);
        long current = state.indexOf(input);
        long lowLimit = state.lowestMatchIndex(current, parameters.windowLog);
        long minChain = current > chainSize ? current - chainSize : 0;
        int attempts = 1 << parameters.searchLog;
        int bestLength = 3;
        long matchIndex = insertAndFindFirstIndex(input, minMatch, state.isLazySkipping);
        for (; matchIndex >= lowLimit && attempts > 0; attempts--) {
            int length = 0;
            if (!isExtended || matchIndex >= dictionaryLimit) {
                int match = state.positionOf(matchIndex);
                if (ZstdMatchState.read32(source, match + bestLength - 3)
                        == ZstdMatchState.read32(source, input + bestLength - 3)) {
                    length = ZstdMatchState.count(source, input, match, inputLimit);
                }
            } else {
                int match = state.dictionaryPositionOf(matchIndex);
                if (ZstdMatchState.read32(dictionary, match) == ZstdMatchState.read32(source, input)) {
                    length = ZstdMatchState.countTwoSegments(source, input + 4, dictionary, match + 4, inputLimit,
                            dictionaryEnd, prefixStart) + 4;
                }
            }
            if (length > bestLength) {
                bestLength = length;
                offsetBase[0] = mask32(current - matchIndex) + ZstdSequenceStore.REPEAT_COUNT;
                if (input + length == inputLimit) {
                    break;
                }
            }
            if (matchIndex <= minChain) {
                break;
            }
            matchIndex = chainTable.getIndex((int) (matchIndex & chainMask));
        }
        return bestLength;
    }

    void fillHashCache(int rowLog, int minMatch, long index, int inputLimit) {
        int hashLog = state.rowHashLog;
        byte[] source = state.baseArray;
        int position = state.positionOf(index);
        long maxToPrefetch = position > inputLimit ? 0 : inputLimit - position + 1;
        long limit = index + Math.min(ROW_HASH_CACHE_SIZE, maxToPrefetch);
        for (long at = index; at < limit; at++) {
            int hash = ZstdMatchState.hashSaltedAt(source, state.positionOf(at), hashLog + ROW_HASH_TAG_BITS,
                    minMatch, state.hashSalt);
            state.hashCache[(int) (at & ROW_HASH_CACHE_MASK)] = hash;
        }
    }

    private int nextCachedHash(long index, int hashLog, int minMatch) {
        int newHash = ZstdMatchState.hashSaltedAt(state.baseArray, state.positionOf(index + ROW_HASH_CACHE_SIZE),
                hashLog + ROW_HASH_TAG_BITS, minMatch, state.hashSalt);
        int slot = (int) (index & ROW_HASH_CACHE_MASK);
        int hash = state.hashCache[slot];
        state.hashCache[slot] = newHash;
        return hash;
    }

    private static int nextRowIndex(byte[] tags, int row, int rowMask) {
        int next = ((tags[row] & 0xFF) - 1) & rowMask;
        if (next == 0) {
            next += rowMask;
        }
        tags[row] = (byte) next;
        return next;
    }

    private void updateRowsFrom(long start, long end, int minMatch, int rowLog, int rowMask, boolean isCached) {
        ZstdIndexTable hashTable = state.hashTable;
        byte[] tags = state.tagTable;
        int hashLog = state.rowHashLog;
        for (long at = start; at < end; at++) {
            int hash = isCached ? nextCachedHash(at, hashLog, minMatch)
                    : ZstdMatchState.hashSaltedAt(state.baseArray, state.positionOf(at), hashLog + ROW_HASH_TAG_BITS,
                    minMatch, state.hashSalt);
            int row = (hash >>> ROW_HASH_TAG_BITS) << rowLog;
            int position = nextRowIndex(tags, row, rowMask);
            tags[row + position] = (byte) (hash & ROW_HASH_TAG_MASK);
            hashTable.setIndex(row + position, at);
        }
    }

    private void updateRows(int input, int minMatch, int rowLog, int rowMask, boolean isCached) {
        long index = state.nextToUpdate;
        long target = state.indexOf(input);
        if (isCached && mask32(target - index) > 384) {
            long bound = index + 96;
            updateRowsFrom(index, bound, minMatch, rowLog, rowMask, isCached);
            index = target - 32;
            fillHashCache(rowLog, minMatch, index, input + 1);
        }
        updateRowsFrom(index, target, minMatch, rowLog, rowMask, isCached);
        state.nextToUpdate = target;
    }

    private static long matchMask(byte[] tags, int row, int tag, int head, int rowEntries) {
        long matches = 0;
        for (int at = 0; at < rowEntries; at++) {
            if ((tags[row + at] & 0xFF) == tag) {
                matches |= 1L << at;
            }
        }
        if (rowEntries == 64) {
            return Long.rotateRight(matches, head);
        }
        int shift = head & (rowEntries - 1);
        long widthMask = (1L << rowEntries) - 1;
        if (shift == 0) {
            return matches;
        }
        return ((matches >>> shift) | (matches << (rowEntries - shift))) & widthMask;
    }

    private int rowFindBestMatch(int input, int inputLimit, long[] offsetBase, int minMatch, int rowLog,
                                 boolean isExtended) {
        ZstdIndexTable hashTable = state.hashTable;
        byte[] tags = state.tagTable;
        int hashLog = state.rowHashLog;
        ZstdCompressionParameters parameters = state.parameters;
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        long dictionaryLimit = state.dictionaryLimit;
        int prefixStart = state.positionOf(dictionaryLimit);
        int dictionaryEnd = state.dictionaryPositionOf(dictionaryLimit);
        long current = state.indexOf(input);
        long lowLimit = state.lowestMatchIndex(current, parameters.windowLog);
        int rowEntries = 1 << rowLog;
        int rowMask = rowEntries - 1;
        int cappedSearchLog = Math.min(parameters.searchLog, rowLog);
        int attempts = 1 << cappedSearchLog;
        int bestLength = 3;
        int hash;
        if (!state.isLazySkipping) {
            updateRows(input, minMatch, rowLog, rowMask, true);
            hash = nextCachedHash(current, hashLog, minMatch);
        } else {
            hash = ZstdMatchState.hashSaltedAt(source, input, hashLog + ROW_HASH_TAG_BITS, minMatch, state.hashSalt);
            state.nextToUpdate = current;
        }
        state.hashSaltEntropy += hash;
        int row = (hash >>> ROW_HASH_TAG_BITS) << rowLog;
        int tag = hash & ROW_HASH_TAG_MASK;
        int head = (tags[row] & 0xFF) & rowMask;
        int matchCount = 0;
        long matches = matchMask(tags, row, tag, head, rowEntries);
        for (; matches != 0 && attempts > 0; matches &= matches - 1) {
            int matchPosition = (head + Long.numberOfTrailingZeros(matches)) & rowMask;
            long matchIndex = hashTable.getIndex(row + matchPosition);
            if (matchPosition == 0) {
                continue;
            }
            if (matchIndex < lowLimit) {
                break;
            }
            matchBuffer[matchCount++] = matchIndex;
            attempts--;
        }
        int position = nextRowIndex(tags, row, rowMask);
        tags[row + position] = (byte) tag;
        hashTable.setIndex(row + position, state.nextToUpdate++);
        for (int at = 0; at < matchCount; at++) {
            long matchIndex = matchBuffer[at];
            int length = 0;
            if (!isExtended || matchIndex >= dictionaryLimit) {
                int match = state.positionOf(matchIndex);
                if (ZstdMatchState.read32(source, match + bestLength - 3)
                        == ZstdMatchState.read32(source, input + bestLength - 3)) {
                    length = ZstdMatchState.count(source, input, match, inputLimit);
                }
            } else {
                int match = state.dictionaryPositionOf(matchIndex);
                if (ZstdMatchState.read32(dictionary, match) == ZstdMatchState.read32(source, input)) {
                    length = ZstdMatchState.countTwoSegments(source, input + 4, dictionary, match + 4, inputLimit,
                            dictionaryEnd, prefixStart) + 4;
                }
            }
            if (length > bestLength) {
                bestLength = length;
                offsetBase[0] = mask32(current - matchIndex) + ZstdSequenceStore.REPEAT_COUNT;
                if (input + length == inputLimit) {
                    break;
                }
            }
        }
        return bestLength;
    }

    private void updateTree(int input, int minMatch) {
        ZstdIndexTable hashTable = state.hashTable;
        int hashLog = state.parameters.hashLog;
        ZstdIndexTable tree = state.chainTable;
        int treeMask = (1 << (state.parameters.chainLog - 1)) - 1;
        byte[] source = state.baseArray;
        long target = state.indexOf(input);
        for (long index = state.nextToUpdate; index < target; index++) {
            int hash = ZstdMatchState.hashAt(source, state.positionOf(index), hashLog, minMatch);
            int matchIndex = hashTable.get(hash);
            int node = 2 * (int) (index & treeMask);
            hashTable.setIndex(hash, index);
            tree.set(node, matchIndex);
            tree.set(node + 1, UNSORTED_MARK);
        }
        state.nextToUpdate = target;
    }

    private void insertTreeNode(long current, int inputEnd, int compares, long treeLow, boolean isExtended) {
        ZstdCompressionParameters parameters = state.parameters;
        ZstdIndexTable tree = state.chainTable;
        int treeMask = (1 << (parameters.chainLog - 1)) - 1;
        long commonSmaller = 0;
        long commonLarger = 0;
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        long dictionaryLimit = state.dictionaryLimit;
        boolean isInPrefix = current >= dictionaryLimit;
        byte[] inputArray = isInPrefix ? source : dictionary;
        int input = isInPrefix ? state.positionOf(current) : state.dictionaryPositionOf(current);
        int end = isInPrefix ? inputEnd : state.dictionaryPositionOf(dictionaryLimit);
        int dictionaryEnd = state.dictionaryPositionOf(dictionaryLimit);
        int prefixStart = state.positionOf(dictionaryLimit);
        int smaller = 2 * (int) (current & treeMask);
        int larger = smaller + 1;
        boolean isSmallerDummy = false;
        boolean isLargerDummy = false;
        long matchIndex = tree.getIndex(smaller);
        long windowValid = state.lowLimit;
        long maxDistance = 1L << parameters.windowLog;
        long windowLow = mask32(current - windowValid) > maxDistance ? current - maxDistance : windowValid;
        int remaining = compares;
        for (; remaining != 0 && matchIndex > windowLow; --remaining) {
            int next = 2 * (int) (matchIndex & treeMask);
            long length = Math.min(commonSmaller, commonLarger);
            byte[] matchArray;
            int match;
            if (!isExtended || matchIndex + length >= dictionaryLimit || current < dictionaryLimit) {
                boolean isFromBase = !isExtended || matchIndex + length >= dictionaryLimit;
                matchArray = isFromBase ? source : dictionary;
                match = isFromBase ? state.positionOf(matchIndex) : state.dictionaryPositionOf(matchIndex);
                length += ZstdMatchState.countAcross(inputArray, input + (int) length, matchArray,
                        match + (int) length, end);
            } else {
                matchArray = dictionary;
                match = state.dictionaryPositionOf(matchIndex);
                length += ZstdMatchState.countTwoSegments(inputArray, input + (int) length, dictionary,
                        match + (int) length, end, dictionaryEnd, prefixStart);
                if (matchIndex + length >= dictionaryLimit) {
                    matchArray = source;
                    match = state.positionOf(matchIndex);
                }
            }
            if (input + length == end) {
                break;
            }
            if ((matchArray[match + (int) length] & 0xFF) < (inputArray[input + (int) length] & 0xFF)) {
                tree.setIndex(smaller, matchIndex);
                commonSmaller = length;
                if (matchIndex <= treeLow) {
                    isSmallerDummy = true;
                    break;
                }
                smaller = next + 1;
                matchIndex = tree.getIndex(next + 1);
            } else {
                tree.setIndex(larger, matchIndex);
                commonLarger = length;
                if (matchIndex <= treeLow) {
                    isLargerDummy = true;
                    break;
                }
                larger = next;
                matchIndex = tree.getIndex(next);
            }
        }
        if (!isSmallerDummy) {
            tree.set(smaller, 0);
        }
        if (!isLargerDummy) {
            tree.set(larger, 0);
        }
    }

    private int treeFindBestMatch(int input, int inputEnd, long[] offsetBase, int minMatch, boolean isExtended) {
        if (input < state.positionOf(state.nextToUpdate)) {
            return 0;
        }
        updateTree(input, minMatch);
        ZstdCompressionParameters parameters = state.parameters;
        ZstdIndexTable hashTable = state.hashTable;
        int hashLog = parameters.hashLog;
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        int hash = ZstdMatchState.hashAt(source, input, hashLog, minMatch);
        long matchIndex = hashTable.getIndex(hash);
        long current = state.indexOf(input);
        long windowLow = state.lowestMatchIndex(current, parameters.windowLog);
        ZstdIndexTable tree = state.chainTable;
        long treeMask = (1L << (parameters.chainLog - 1)) - 1;
        long treeLow = treeMask >= current ? 0 : current - treeMask;
        long unsortLimit = Math.max(treeLow, windowLow);
        int nextCandidate = 2 * (int) (matchIndex & treeMask);
        int unsortedMark = nextCandidate + 1;
        int compares = 1 << parameters.searchLog;
        int candidates = compares;
        long previousCandidate = 0;
        while (matchIndex > unsortLimit && tree.get(unsortedMark) == UNSORTED_MARK && candidates > 1) {
            tree.setIndex(unsortedMark, previousCandidate);
            previousCandidate = matchIndex;
            matchIndex = tree.getIndex(nextCandidate);
            nextCandidate = 2 * (int) (matchIndex & treeMask);
            unsortedMark = nextCandidate + 1;
            candidates--;
        }
        if (matchIndex > unsortLimit && tree.get(unsortedMark) == UNSORTED_MARK) {
            tree.set(nextCandidate, 0);
            tree.set(unsortedMark, 0);
        }
        matchIndex = previousCandidate;
        while (matchIndex != 0) {
            int nextIndexAt = 2 * (int) (matchIndex & treeMask) + 1;
            long nextIndex = tree.getIndex(nextIndexAt);
            insertTreeNode(matchIndex, inputEnd, candidates, unsortLimit, isExtended);
            matchIndex = nextIndex;
            candidates++;
        }
        long commonSmaller = 0;
        long commonLarger = 0;
        long dictionaryLimit = state.dictionaryLimit;
        int dictionaryEnd = state.dictionaryPositionOf(dictionaryLimit);
        int prefixStart = state.positionOf(dictionaryLimit);
        int smaller = 2 * (int) (current & treeMask);
        int larger = smaller + 1;
        boolean isSmallerDummy = false;
        boolean isLargerDummy = false;
        long matchEndIndex = current + 8 + 1;
        long bestLength = 0;
        matchIndex = hashTable.getIndex(hash);
        hashTable.setIndex(hash, current);
        for (; compares != 0 && matchIndex > windowLow; --compares) {
            int next = 2 * (int) (matchIndex & treeMask);
            long length = Math.min(commonSmaller, commonLarger);
            byte[] matchArray;
            int match;
            if (!isExtended || matchIndex + length >= dictionaryLimit) {
                matchArray = source;
                match = state.positionOf(matchIndex);
                length += ZstdMatchState.count(source, input + (int) length, match + (int) length, inputEnd);
            } else {
                matchArray = dictionary;
                match = state.dictionaryPositionOf(matchIndex);
                length += ZstdMatchState.countTwoSegments(source, input + (int) length, dictionary,
                        match + (int) length, inputEnd, dictionaryEnd, prefixStart);
                if (matchIndex + length >= dictionaryLimit) {
                    matchArray = source;
                    match = state.positionOf(matchIndex);
                }
            }
            if (length > bestLength) {
                if (length > mask32(matchEndIndex - matchIndex)) {
                    matchEndIndex = matchIndex + length;
                }
                if (4 * (int) (length - bestLength) > highBit(current - matchIndex + 1) - highBit(offsetBase[0])) {
                    bestLength = length;
                    offsetBase[0] = mask32(current - matchIndex) + ZstdSequenceStore.REPEAT_COUNT;
                }
                if (input + length == inputEnd) {
                    break;
                }
            }
            if ((matchArray[match + (int) length] & 0xFF) < (source[input + (int) length] & 0xFF)) {
                tree.setIndex(smaller, matchIndex);
                commonSmaller = length;
                if (matchIndex <= treeLow) {
                    isSmallerDummy = true;
                    break;
                }
                smaller = next + 1;
                matchIndex = tree.getIndex(next + 1);
            } else {
                tree.setIndex(larger, matchIndex);
                commonLarger = length;
                if (matchIndex <= treeLow) {
                    isLargerDummy = true;
                    break;
                }
                larger = next;
                matchIndex = tree.getIndex(next);
            }
        }
        if (!isSmallerDummy) {
            tree.set(smaller, 0);
        }
        if (!isLargerDummy) {
            tree.set(larger, 0);
        }
        state.nextToUpdate = mask32(matchEndIndex - 8);
        return (int) bestLength;
    }

    private int searchMax(int input, int inputEnd, long[] offsetBase, int minMatch, int rowLog, int method,
                          boolean isExtended) {
        return switch (method) {
            case BINARY_TREE -> treeFindBestMatch(input, inputEnd, offsetBase, minMatch, isExtended);
            case ROW_HASH -> rowFindBestMatch(input, inputEnd, offsetBase, minMatch, rowLog, isExtended);
            default -> hashChainFindBestMatch(input, inputEnd, offsetBase, minMatch, isExtended);
        };
    }

    int compressBlock(ZstdSequenceStore store, int[] repeats, int from, int size, int method, int depth) {
        ZstdCompressionParameters parameters = state.parameters;
        byte[] source = state.baseArray;
        int ip = from;
        int anchor = from;
        int end = from + size;
        int limit = method == ROW_HASH ? end - 8 - ROW_HASH_CACHE_SIZE : end - 8;
        long prefixLowestIndex = state.dictionaryLimit;
        int prefixLowest = state.positionOf(prefixLowestIndex);
        int minMatch = Math.max(4, Math.min(parameters.minMatch, 6));
        int rowLog = Math.max(4, Math.min(parameters.searchLog, 6));
        int repeat1 = repeats[0];
        int repeat2 = repeats[1];
        int saved1 = 0;
        int saved2 = 0;
        if (ip == prefixLowest) {
            ip++;
        }
        long first = state.indexOf(ip);
        long windowLow = state.lowestPrefixIndex(first, parameters.windowLog);
        long maxRepeat = mask32(first - windowLow);
        if ((repeat2 & 0xFFFFFFFFL) > maxRepeat) {
            saved2 = repeat2;
            repeat2 = 0;
        }
        if ((repeat1 & 0xFFFFFFFFL) > maxRepeat) {
            saved1 = repeat1;
            repeat1 = 0;
        }
        state.isLazySkipping = false;
        if (method == ROW_HASH) {
            fillHashCache(rowLog, minMatch, state.nextToUpdate, limit);
        }
        while (ip < limit) {
            long matchLength = 0;
            long offBase = 1;
            int start = ip + 1;
            boolean isStoredNow = false;
            if (repeat1 > 0 && ZstdMatchState.read32(source, ip + 1 - repeat1) == ZstdMatchState.read32(source, ip + 1)) {
                matchLength = ZstdMatchState.count(source, ip + 1 + 4, ip + 1 + 4 - repeat1, end) + 4;
                if (depth == 0) {
                    isStoredNow = true;
                }
            }
            if (!isStoredNow) {
                offsetFound[0] = NOT_FOUND;
                int found = searchMax(ip, end, offsetFound, minMatch, rowLog, method, false);
                if (found > matchLength) {
                    matchLength = found;
                    start = ip;
                    offBase = offsetFound[0];
                }
                if (matchLength < 4) {
                    long step = ((long) (ip - anchor) >> ZstdMatchState.SEARCH_STRENGTH) + 1;
                    ip += (int) step;
                    state.isLazySkipping = step > LAZY_SKIPPING_STEP;
                    continue;
                }
                if (depth >= 1) {
                    while (ip < limit) {
                        ip++;
                        if (offBase != 0 && repeat1 > 0
                                && ZstdMatchState.read32(source, ip) == ZstdMatchState.read32(source, ip - repeat1)) {
                            long repeatLength = ZstdMatchState.count(source, ip + 4, ip + 4 - repeat1, end) + 4;
                            int gain2 = (int) (repeatLength * 3);
                            int gain1 = (int) (matchLength * 3 - highBit(offBase) + 1);
                            if (repeatLength >= 4 && gain2 > gain1) {
                                matchLength = repeatLength;
                                offBase = 1;
                                start = ip;
                            }
                        }
                        offsetFound[0] = NOT_FOUND;
                        int found2 = searchMax(ip, end, offsetFound, minMatch, rowLog, method, false);
                        int gain2 = (int) ((long) found2 * 4 - highBit(offsetFound[0]));
                        int gain1 = (int) (matchLength * 4 - highBit(offBase) + 4);
                        if (found2 >= 4 && gain2 > gain1) {
                            matchLength = found2;
                            offBase = offsetFound[0];
                            start = ip;
                            continue;
                        }
                        if (depth == 2 && ip < limit) {
                            ip++;
                            if (offBase != 0 && repeat1 > 0 && ZstdMatchState.read32(source, ip)
                                    == ZstdMatchState.read32(source, ip - repeat1)) {
                                long repeatLength = ZstdMatchState.count(source, ip + 4, ip + 4 - repeat1, end) + 4;
                                int repeatGain = (int) (repeatLength * 4);
                                int currentGain = (int) (matchLength * 4 - highBit(offBase) + 1);
                                if (repeatLength >= 4 && repeatGain > currentGain) {
                                    matchLength = repeatLength;
                                    offBase = 1;
                                    start = ip;
                                }
                            }
                            offsetFound[0] = NOT_FOUND;
                            int found3 = searchMax(ip, end, offsetFound, minMatch, rowLog, method, false);
                            int candidateGain = (int) ((long) found3 * 4 - highBit(offsetFound[0]));
                            int currentGain = (int) (matchLength * 4 - highBit(offBase) + 7);
                            if (found3 >= 4 && candidateGain > currentGain) {
                                matchLength = found3;
                                offBase = offsetFound[0];
                                start = ip;
                                continue;
                            }
                        }
                        break;
                    }
                }
                if (offBase > ZstdSequenceStore.REPEAT_COUNT) {
                    int offset = (int) (offBase - ZstdSequenceStore.REPEAT_COUNT);
                    while (start > anchor && start - offset > prefixLowest
                            && source[start - 1] == source[start - offset - 1]) {
                        start--;
                        matchLength++;
                    }
                    repeat2 = repeat1;
                    repeat1 = offset;
                }
            }
            store.storeSequence(start - anchor, source, anchor, (int) offBase, (int) matchLength);
            ip = start + (int) matchLength;
            anchor = ip;
            if (state.isLazySkipping) {
                if (method == ROW_HASH) {
                    fillHashCache(rowLog, minMatch, state.nextToUpdate, limit);
                }
                state.isLazySkipping = false;
            }
            while (ip <= limit && repeat2 > 0
                    && ZstdMatchState.read32(source, ip) == ZstdMatchState.read32(source, ip - repeat2)) {
                int repeatLength = ZstdMatchState.count(source, ip + 4, ip + 4 - repeat2, end) + 4;
                int swapped = repeat2;
                repeat2 = repeat1;
                repeat1 = swapped;
                store.storeSequence(0, source, anchor, 1, repeatLength);
                ip += repeatLength;
                anchor = ip;
            }
        }
        saved2 = saved1 != 0 && repeat1 != 0 ? saved1 : saved2;
        repeats[0] = repeat1 != 0 ? repeat1 : saved1;
        repeats[1] = repeat2 != 0 ? repeat2 : saved2;
        return end - anchor;
    }

    private boolean isRepeatValid(long repeatIndex, long dictionaryLimit, int offset, long current, int windowLog) {
        long windowLow = state.lowestMatchIndex(current, windowLog);
        return ZstdMatchState.isOutsideOverlap(dictionaryLimit, repeatIndex)
                && (offset & 0xFFFFFFFFL) <= mask32(current - windowLow);
    }

    private int repeatMatchLength(int ip, long repeatIndex, int end, long dictionaryLimit, int dictionaryEnd,
                                  int prefixStart) {
        byte[] source = state.baseArray;
        boolean isInDictionary = repeatIndex < dictionaryLimit;
        byte[] repeatArray = isInDictionary ? state.dictionaryArray : source;
        int repeatMatch = isInDictionary ? state.dictionaryPositionOf(repeatIndex) : state.positionOf(repeatIndex);
        if (ZstdMatchState.read32(source, ip) != ZstdMatchState.read32(repeatArray, repeatMatch)) {
            return -1;
        }
        int repeatEnd = isInDictionary ? dictionaryEnd : end;
        return ZstdMatchState.countTwoSegments(source, ip + 4, repeatArray, repeatMatch + 4, end, repeatEnd,
                prefixStart) + 4;
    }

    int compressBlockWithDictionary(ZstdSequenceStore store, int[] repeats, int from, int size, int method,
                                    int depth) {
        ZstdCompressionParameters parameters = state.parameters;
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        int ip = from;
        int anchor = from;
        int end = from + size;
        int limit = method == ROW_HASH ? end - 8 - ROW_HASH_CACHE_SIZE : end - 8;
        long dictionaryLimit = state.dictionaryLimit;
        int prefixStart = state.positionOf(dictionaryLimit);
        int dictionaryEnd = state.dictionaryPositionOf(dictionaryLimit);
        int dictionaryStart = state.dictionaryPositionOf(state.lowLimit);
        int windowLog = parameters.windowLog;
        int minMatch = Math.max(4, Math.min(parameters.minMatch, 6));
        int rowLog = Math.max(4, Math.min(parameters.searchLog, 6));
        int repeat1 = repeats[0];
        int repeat2 = repeats[1];
        state.isLazySkipping = false;
        if (ip == prefixStart) {
            ip++;
        }
        if (method == ROW_HASH) {
            fillHashCache(rowLog, minMatch, state.nextToUpdate, limit);
        }
        while (ip < limit) {
            long matchLength = 0;
            long offBase = 1;
            int start = ip + 1;
            long current = state.indexOf(ip);
            boolean isStoredNow = false;
            long repeatIndex = mask32(current + 1 - repeat1);
            if (isRepeatValid(repeatIndex, dictionaryLimit, repeat1, current + 1, windowLog)) {
                int length = repeatMatchLength(ip + 1, repeatIndex, end, dictionaryLimit, dictionaryEnd, prefixStart);
                if (length >= 0) {
                    matchLength = length;
                    if (depth == 0) {
                        isStoredNow = true;
                    }
                }
            }
            if (!isStoredNow) {
                offsetFound[0] = NOT_FOUND;
                int found = searchMax(ip, end, offsetFound, minMatch, rowLog, method, true);
                if (found > matchLength) {
                    matchLength = found;
                    start = ip;
                    offBase = offsetFound[0];
                }
                if (matchLength < 4) {
                    long step = (long) (ip - anchor) >> ZstdMatchState.SEARCH_STRENGTH;
                    ip += (int) (step + 1);
                    state.isLazySkipping = step > LAZY_SKIPPING_STEP;
                    continue;
                }
                if (depth >= 1) {
                    while (ip < limit) {
                        ip++;
                        current++;
                        if (offBase != 0) {
                            long index = mask32(current - repeat1);
                            if (isRepeatValid(index, dictionaryLimit, repeat1, current, windowLog)) {
                                int repeatLength = repeatMatchLength(ip, index, end, dictionaryLimit, dictionaryEnd,
                                        prefixStart);
                                if (repeatLength >= 0) {
                                    int gain2 = repeatLength * 3;
                                    int gain1 = (int) (matchLength * 3 - highBit(offBase) + 1);
                                    if (repeatLength >= 4 && gain2 > gain1) {
                                        matchLength = repeatLength;
                                        offBase = 1;
                                        start = ip;
                                    }
                                }
                            }
                        }
                        offsetFound[0] = NOT_FOUND;
                        int found2 = searchMax(ip, end, offsetFound, minMatch, rowLog, method, true);
                        int gain2 = (int) ((long) found2 * 4 - highBit(offsetFound[0]));
                        int gain1 = (int) (matchLength * 4 - highBit(offBase) + 4);
                        if (found2 >= 4 && gain2 > gain1) {
                            matchLength = found2;
                            offBase = offsetFound[0];
                            start = ip;
                            continue;
                        }
                        if (depth == 2 && ip < limit) {
                            ip++;
                            current++;
                            if (offBase != 0) {
                                long index = mask32(current - repeat1);
                                if (isRepeatValid(index, dictionaryLimit, repeat1, current, windowLog)) {
                                    int repeatLength = repeatMatchLength(ip, index, end, dictionaryLimit,
                                            dictionaryEnd, prefixStart);
                                    if (repeatLength >= 0) {
                                        int repeatGain = repeatLength * 4;
                                        int currentGain = (int) (matchLength * 4 - highBit(offBase) + 1);
                                        if (repeatLength >= 4 && repeatGain > currentGain) {
                                            matchLength = repeatLength;
                                            offBase = 1;
                                            start = ip;
                                        }
                                    }
                                }
                            }
                            offsetFound[0] = NOT_FOUND;
                            int found3 = searchMax(ip, end, offsetFound, minMatch, rowLog, method, true);
                            int candidateGain = (int) ((long) found3 * 4 - highBit(offsetFound[0]));
                            int currentGain = (int) (matchLength * 4 - highBit(offBase) + 7);
                            if (found3 >= 4 && candidateGain > currentGain) {
                                matchLength = found3;
                                offBase = offsetFound[0];
                                start = ip;
                                continue;
                            }
                        }
                        break;
                    }
                }
                if (offBase > ZstdSequenceStore.REPEAT_COUNT) {
                    int offset = (int) (offBase - ZstdSequenceStore.REPEAT_COUNT);
                    long matchIndex = mask32(state.indexOf(start) - offset);
                    boolean isInDictionary = matchIndex < dictionaryLimit;
                    byte[] matchArray = isInDictionary ? dictionary : source;
                    int match = isInDictionary ? state.dictionaryPositionOf(matchIndex) : state.positionOf(matchIndex);
                    int matchStart = isInDictionary ? dictionaryStart : prefixStart;
                    while (start > anchor && match > matchStart && source[start - 1] == matchArray[match - 1]) {
                        start--;
                        match--;
                        matchLength++;
                    }
                    repeat2 = repeat1;
                    repeat1 = offset;
                }
            }
            store.storeSequence(start - anchor, source, anchor, (int) offBase, (int) matchLength);
            ip = start + (int) matchLength;
            anchor = ip;
            if (state.isLazySkipping) {
                if (method == ROW_HASH) {
                    fillHashCache(rowLog, minMatch, state.nextToUpdate, limit);
                }
                state.isLazySkipping = false;
            }
            while (ip <= limit) {
                long repeatCurrent = state.indexOf(ip);
                long index = mask32(repeatCurrent - repeat2);
                if (isRepeatValid(index, dictionaryLimit, repeat2, repeatCurrent, windowLog)) {
                    int repeatLength = repeatMatchLength(ip, index, end, dictionaryLimit, dictionaryEnd, prefixStart);
                    if (repeatLength >= 0) {
                        int swapped = repeat2;
                        repeat2 = repeat1;
                        repeat1 = swapped;
                        store.storeSequence(0, source, anchor, 1, repeatLength);
                        ip += repeatLength;
                        anchor = ip;
                        continue;
                    }
                }
                break;
            }
        }
        repeats[0] = repeat1;
        repeats[1] = repeat2;
        return end - anchor;
    }
}
