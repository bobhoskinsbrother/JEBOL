package org.jebol.domain.eval.compression.zstd;

final class ZstdFastMatcher {

    private static final int STEP_INCREMENT = 1 << (ZstdMatchState.SEARCH_STRENGTH - 1);
    private static final int FILL_STEP = 3;

    private ZstdFastMatcher() {
    }

    private static int minMatchOf(ZstdMatchState state) {
        int minMatch = state.parameters.minMatch;
        return minMatch >= 4 && minMatch <= 7 ? minMatch : 4;
    }

    static void fillHashTable(ZstdMatchState state, int end, boolean isFull) {
        ZstdIndexTable table = state.hashTable;
        int bits = state.parameters.hashLog;
        int minMatch = state.parameters.minMatch;
        byte[] source = state.baseArray;
        int input = state.positionOf(state.nextToUpdate);
        int limit = end - ZstdMatchState.HASH_READ_SIZE;
        for (; input + FILL_STEP < limit + 2; input += FILL_STEP) {
            long current = state.indexOf(input);
            table.setIndex(ZstdMatchState.hashAt(source, input, bits, minMatch), current);
            if (!isFull) {
                continue;
            }
            for (int step = 1; step < FILL_STEP; step++) {
                int hash = ZstdMatchState.hashAt(source, input + step, bits, minMatch);
                if (table.get(hash) == 0) {
                    table.setIndex(hash, current + step);
                }
            }
        }
    }

    static int compressBlock(ZstdMatchState state, ZstdSequenceStore store, int[] repeats, int from, int size) {
        return compressPrefix(state, store, repeats, from, size, minMatchOf(state));
    }

    private static int compressPrefix(ZstdMatchState state, ZstdSequenceStore store, int[] repeats, int from,
                                      int size, int minMatch) {
        ZstdCompressionParameters parameters = state.parameters;
        ZstdIndexTable table = state.hashTable;
        int hashLog = parameters.hashLog;
        int stepSize = parameters.targetLength + (parameters.targetLength == 0 ? 1 : 0) + 1;
        byte[] source = state.baseArray;
        long endIndex = state.indexOf(from) + size;
        long prefixStartIndex = state.lowestPrefixIndex(endIndex, parameters.windowLog);
        int prefixStart = state.positionOf(prefixStartIndex);
        int end = from + size;
        int limit = end - ZstdMatchState.HASH_READ_SIZE;
        int anchor = from;
        int ip0 = from;
        int repeat1 = repeats[0];
        int repeat2 = repeats[1];
        int saved1 = 0;
        int saved2 = 0;
        if (ip0 == prefixStart) {
            ip0++;
        }
        long first = state.indexOf(ip0);
        long windowLow = state.lowestPrefixIndex(first, parameters.windowLog);
        long maxRepeat = (first - windowLow) & 0xFFFFFFFFL;
        if ((repeat2 & 0xFFFFFFFFL) > maxRepeat) {
            saved2 = repeat2;
            repeat2 = 0;
        }
        if ((repeat1 & 0xFFFFFFFFL) > maxRepeat) {
            saved1 = repeat1;
            repeat1 = 0;
        }
        for (;;) {
            int step = stepSize;
            int nextStep = ip0 + STEP_INCREMENT;
            int ip1 = ip0 + 1;
            int ip2 = ip0 + step;
            int ip3 = ip2 + 1;
            if (ip3 >= limit) {
                break;
            }
            int hash0 = ZstdMatchState.hashAt(source, ip0, hashLog, minMatch);
            int hash1 = ZstdMatchState.hashAt(source, ip1, hashLog, minMatch);
            long matchIndex = table.getIndex(hash0);
            long current0;
            int match0;
            int matchLength;
            int offBase;
            boolean isRepeat = false;
            boolean isFound = false;
            do {
                int repeatValue = ZstdMatchState.read32(source, ip2 - repeat1);
                current0 = state.indexOf(ip0);
                table.setIndex(hash0, current0);
                if (ZstdMatchState.read32(source, ip2) == repeatValue && repeat1 > 0) {
                    table.setIndex(hash1, state.indexOf(ip1));
                    isRepeat = true;
                    ip0 = ip2;
                    break;
                }
                if (isMatchFound(source, ip0, state, matchIndex, prefixStartIndex)) {
                    table.setIndex(hash1, state.indexOf(ip1));
                    isFound = true;
                    break;
                }
                matchIndex = table.getIndex(hash1);
                hash0 = hash1;
                hash1 = ZstdMatchState.hashAt(source, ip2, hashLog, minMatch);
                ip0 = ip1;
                ip1 = ip2;
                ip2 = ip3;
                current0 = state.indexOf(ip0);
                table.setIndex(hash0, current0);
                if (isMatchFound(source, ip0, state, matchIndex, prefixStartIndex)) {
                    if (step <= 4) {
                        table.setIndex(hash1, state.indexOf(ip1));
                    }
                    isFound = true;
                    break;
                }
                matchIndex = table.getIndex(hash1);
                hash0 = hash1;
                hash1 = ZstdMatchState.hashAt(source, ip2, hashLog, minMatch);
                ip0 = ip1;
                ip1 = ip2;
                ip2 = ip0 + step;
                ip3 = ip1 + step;
                if (ip2 >= nextStep) {
                    step++;
                    nextStep += STEP_INCREMENT;
                }
            } while (ip3 < limit);
            if (!isRepeat && !isFound) {
                break;
            }
            if (isRepeat) {
                match0 = ip0 - repeat1;
                matchLength = source[ip0 - 1] == source[match0 - 1] ? 1 : 0;
                ip0 -= matchLength;
                match0 -= matchLength;
                offBase = 1;
                matchLength += 4;
            } else {
                match0 = state.positionOf(matchIndex);
                repeat2 = repeat1;
                repeat1 = ip0 - match0;
                offBase = repeat1 + ZstdSequenceStore.REPEAT_COUNT;
                matchLength = 4;
                while (ip0 > anchor && match0 > prefixStart && source[ip0 - 1] == source[match0 - 1]) {
                    ip0--;
                    match0--;
                    matchLength++;
                }
            }
            matchLength += ZstdMatchState.count(source, ip0 + matchLength, match0 + matchLength, end);
            store.storeSequence(ip0 - anchor, source, anchor, offBase, matchLength);
            ip0 += matchLength;
            anchor = ip0;
            if (ip0 <= limit) {
                int afterCurrent = state.positionOf(current0 + 2);
                table.setIndex(ZstdMatchState.hashAt(source, afterCurrent, hashLog, minMatch), current0 + 2);
                table.setIndex(ZstdMatchState.hashAt(source, ip0 - 2, hashLog, minMatch), state.indexOf(ip0 - 2));
                if (repeat2 > 0) {
                    while (ip0 <= limit
                            && ZstdMatchState.read32(source, ip0) == ZstdMatchState.read32(source, ip0 - repeat2)) {
                        int repeatLength = ZstdMatchState.count(source, ip0 + 4, ip0 + 4 - repeat2, end) + 4;
                        int swapped = repeat2;
                        repeat2 = repeat1;
                        repeat1 = swapped;
                        table.setIndex(ZstdMatchState.hashAt(source, ip0, hashLog, minMatch), state.indexOf(ip0));
                        ip0 += repeatLength;
                        store.storeSequence(0, source, anchor, 1, repeatLength);
                        anchor = ip0;
                    }
                }
            }
        }
        saved2 = saved1 != 0 && repeat1 != 0 ? saved1 : saved2;
        repeats[0] = repeat1 != 0 ? repeat1 : saved1;
        repeats[1] = repeat2 != 0 ? repeat2 : saved2;
        return end - anchor;
    }

    private static boolean isMatchFound(byte[] source, int input, ZstdMatchState state, long matchIndex,
                                        long lowLimit) {
        if (matchIndex < lowLimit) {
            return false;
        }
        return ZstdMatchState.read32(source, input) == ZstdMatchState.read32(source, state.positionOf(matchIndex));
    }

    static int compressBlockWithDictionary(ZstdMatchState state, ZstdSequenceStore store, int[] repeats, int from,
                                           int size) {
        ZstdCompressionParameters parameters = state.parameters;
        int minMatch = minMatchOf(state);
        ZstdIndexTable table = state.hashTable;
        int hashLog = parameters.hashLog;
        int stepSize = parameters.targetLength + (parameters.targetLength == 0 ? 1 : 0) + 1;
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        int anchor = from;
        long endIndex = state.indexOf(from) + size;
        long lowLimit = state.lowestMatchIndex(endIndex, parameters.windowLog);
        long dictionaryStartIndex = lowLimit;
        int dictionaryStart = state.dictionaryPositionOf(dictionaryStartIndex);
        long dictionaryLimit = state.dictionaryLimit;
        long prefixStartIndex = dictionaryLimit < lowLimit ? lowLimit : dictionaryLimit;
        int prefixStart = state.positionOf(prefixStartIndex);
        int dictionaryEnd = state.dictionaryPositionOf(prefixStartIndex);
        int end = from + size;
        int limit = end - 8;
        int repeat1 = repeats[0];
        int repeat2 = repeats[1];
        int saved1 = 0;
        int saved2 = 0;
        int ip0 = from;
        if (prefixStartIndex == dictionaryStartIndex) {
            return compressBlock(state, store, repeats, from, size);
        }
        long first = state.indexOf(ip0);
        long maxRepeat = (first - dictionaryStartIndex) & 0xFFFFFFFFL;
        if ((repeat2 & 0xFFFFFFFFL) >= maxRepeat) {
            saved2 = repeat2;
            repeat2 = 0;
        }
        if ((repeat1 & 0xFFFFFFFFL) >= maxRepeat) {
            saved1 = repeat1;
            repeat1 = 0;
        }
        for (;;) {
            int step = stepSize;
            int nextStep = ip0 + STEP_INCREMENT;
            int ip1 = ip0 + 1;
            int ip2 = ip0 + step;
            int ip3 = ip2 + 1;
            if (ip3 >= limit) {
                break;
            }
            int hash0 = ZstdMatchState.hashAt(source, ip0, hashLog, minMatch);
            int hash1 = ZstdMatchState.hashAt(source, ip1, hashLog, minMatch);
            long index = table.getIndex(hash0);
            long current0 = 0;
            byte[] matchArray = source;
            int match0 = 0;
            int matchEnd = 0;
            int matchLength = 0;
            int offBase = 0;
            boolean isMatched = false;
            boolean isFound = false;
            do {
                long current2 = state.indexOf(ip2);
                long repeatIndex = (current2 - repeat1) & 0xFFFFFFFFL;
                boolean isRepeatInDictionary = repeatIndex < prefixStartIndex;
                int repeatValue;
                if (((prefixStartIndex - repeatIndex) & 0xFFFFFFFFL) >= 4 && repeat1 > 0) {
                    repeatValue = isRepeatInDictionary
                            ? ZstdMatchState.read32(dictionary, state.dictionaryPositionOf(repeatIndex))
                            : ZstdMatchState.read32(source, state.positionOf(repeatIndex));
                } else {
                    repeatValue = ZstdMatchState.read32(source, ip2) ^ 1;
                }
                current0 = state.indexOf(ip0);
                table.setIndex(hash0, current0);
                if (ZstdMatchState.read32(source, ip2) == repeatValue) {
                    ip0 = ip2;
                    matchArray = isRepeatInDictionary ? dictionary : source;
                    match0 = isRepeatInDictionary ? state.dictionaryPositionOf(repeatIndex)
                            : state.positionOf(repeatIndex);
                    matchEnd = isRepeatInDictionary ? dictionaryEnd : end;
                    matchLength = source[ip0 - 1] == matchArray[match0 - 1] ? 1 : 0;
                    ip0 -= matchLength;
                    match0 -= matchLength;
                    offBase = 1;
                    matchLength += 4;
                    isMatched = true;
                    break;
                }
                if (isIndexMatching(state, source, ip0, index, dictionaryStartIndex, prefixStartIndex)) {
                    isFound = true;
                    break;
                }
                index = table.getIndex(hash1);
                hash0 = hash1;
                hash1 = ZstdMatchState.hashAt(source, ip2, hashLog, minMatch);
                ip0 = ip1;
                ip1 = ip2;
                ip2 = ip3;
                current0 = state.indexOf(ip0);
                table.setIndex(hash0, current0);
                if (isIndexMatching(state, source, ip0, index, dictionaryStartIndex, prefixStartIndex)) {
                    isFound = true;
                    break;
                }
                index = table.getIndex(hash1);
                hash0 = hash1;
                hash1 = ZstdMatchState.hashAt(source, ip2, hashLog, minMatch);
                ip0 = ip1;
                ip1 = ip2;
                ip2 = ip0 + step;
                ip3 = ip1 + step;
                if (ip2 >= nextStep) {
                    step++;
                    nextStep += STEP_INCREMENT;
                }
            } while (ip3 < limit);
            if (!isMatched) {
                if (!isFound) {
                    break;
                }
                int offset = (int) (current0 - index);
                boolean isInDictionary = index < prefixStartIndex;
                int lowMatch = isInDictionary ? dictionaryStart : prefixStart;
                matchEnd = isInDictionary ? dictionaryEnd : end;
                matchArray = isInDictionary ? dictionary : source;
                match0 = isInDictionary ? state.dictionaryPositionOf(index) : state.positionOf(index);
                repeat2 = repeat1;
                repeat1 = offset;
                offBase = offset + ZstdSequenceStore.REPEAT_COUNT;
                matchLength = 4;
                while (ip0 > anchor && match0 > lowMatch && source[ip0 - 1] == matchArray[match0 - 1]) {
                    ip0--;
                    match0--;
                    matchLength++;
                }
            }
            matchLength += ZstdMatchState.countTwoSegments(source, ip0 + matchLength, matchArray,
                    match0 + matchLength, end, matchEnd, prefixStart);
            store.storeSequence(ip0 - anchor, source, anchor, offBase, matchLength);
            ip0 += matchLength;
            anchor = ip0;
            if (ip1 < ip0) {
                table.setIndex(hash1, state.indexOf(ip1));
            }
            if (ip0 <= limit) {
                int afterCurrent = state.positionOf(current0 + 2);
                table.setIndex(ZstdMatchState.hashAt(source, afterCurrent, hashLog, minMatch), current0 + 2);
                table.setIndex(ZstdMatchState.hashAt(source, ip0 - 2, hashLog, minMatch), state.indexOf(ip0 - 2));
                while (ip0 <= limit) {
                    long repeatIndex2 = (state.indexOf(ip0) - repeat2) & 0xFFFFFFFFL;
                    boolean isInDictionary = repeatIndex2 < prefixStartIndex;
                    byte[] repeatArray = isInDictionary ? dictionary : source;
                    int repeatMatch2 = isInDictionary ? state.dictionaryPositionOf(repeatIndex2)
                            : state.positionOf(repeatIndex2);
                    if (ZstdMatchState.isOutsideOverlap(prefixStartIndex, repeatIndex2) && repeat2 > 0
                            && ZstdMatchState.read32(repeatArray, repeatMatch2)
                            == ZstdMatchState.read32(source, ip0)) {
                        int repeatEnd2 = isInDictionary ? dictionaryEnd : end;
                        int repeatLength2 = ZstdMatchState.countTwoSegments(source, ip0 + 4, repeatArray,
                                repeatMatch2 + 4, end, repeatEnd2, prefixStart) + 4;
                        int swapped = repeat2;
                        repeat2 = repeat1;
                        repeat1 = swapped;
                        store.storeSequence(0, source, anchor, 1, repeatLength2);
                        table.setIndex(ZstdMatchState.hashAt(source, ip0, hashLog, minMatch), state.indexOf(ip0));
                        ip0 += repeatLength2;
                        anchor = ip0;
                        continue;
                    }
                    break;
                }
            }
        }
        saved2 = saved1 != 0 && repeat1 != 0 ? saved1 : saved2;
        repeats[0] = repeat1 != 0 ? repeat1 : saved1;
        repeats[1] = repeat2 != 0 ? repeat2 : saved2;
        return end - anchor;
    }

    private static boolean isIndexMatching(ZstdMatchState state, byte[] source, int input, long index,
                                           long dictionaryStartIndex, long prefixStartIndex) {
        if (index < dictionaryStartIndex) {
            return false;
        }
        int value = index < prefixStartIndex
                ? ZstdMatchState.read32(state.dictionaryArray, state.dictionaryPositionOf(index))
                : ZstdMatchState.read32(source, state.positionOf(index));
        return ZstdMatchState.read32(source, input) == value;
    }
}
