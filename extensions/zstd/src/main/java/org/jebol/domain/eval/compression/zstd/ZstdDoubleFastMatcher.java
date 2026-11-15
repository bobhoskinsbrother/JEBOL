package org.jebol.domain.eval.compression.zstd;

final class ZstdDoubleFastMatcher {

    private static final int STEP_INCREMENT = 1 << ZstdMatchState.SEARCH_STRENGTH;

    private ZstdDoubleFastMatcher() {
    }

    private static int minMatchOf(ZstdMatchState state) {
        int minMatch = state.parameters.minMatch;
        return minMatch >= 4 && minMatch <= 7 ? minMatch : 4;
    }

    static void fillHashTable(ZstdMatchState state, int end, boolean isFull) {
        ZstdIndexTable large = state.hashTable;
        ZstdIndexTable small = state.chainTable;
        int largeBits = state.parameters.hashLog;
        int smallBits = state.parameters.chainLog;
        int minMatch = state.parameters.minMatch;
        byte[] source = state.baseArray;
        int input = state.positionOf(state.nextToUpdate);
        int limit = end - ZstdMatchState.HASH_READ_SIZE;
        for (; input + 2 <= limit; input += 3) {
            long current = state.indexOf(input);
            for (int step = 0; step < 3; step++) {
                int smallHash = ZstdMatchState.hashAt(source, input + step, smallBits, minMatch);
                int largeHash = ZstdMatchState.hashAt(source, input + step, largeBits, 8);
                if (step == 0) {
                    small.setIndex(smallHash, current + step);
                }
                if (step == 0 || large.get(largeHash) == 0) {
                    large.setIndex(largeHash, current + step);
                }
                if (!isFull) {
                    break;
                }
            }
        }
    }

    static int compressBlock(ZstdMatchState state, ZstdSequenceStore store, int[] repeats, int from, int size) {
        ZstdCompressionParameters parameters = state.parameters;
        int minMatch = minMatchOf(state);
        ZstdIndexTable hashLong = state.hashTable;
        int longBits = parameters.hashLog;
        ZstdIndexTable hashSmall = state.chainTable;
        int smallBits = parameters.chainLog;
        byte[] source = state.baseArray;
        int anchor = from;
        long endIndex = state.indexOf(from) + size;
        long prefixLowestIndex = state.lowestPrefixIndex(endIndex, parameters.windowLog);
        int prefixLowest = state.positionOf(prefixLowestIndex);
        int end = from + size;
        int limit = end - ZstdMatchState.HASH_READ_SIZE;
        int repeat1 = repeats[0];
        int repeat2 = repeats[1];
        int saved1 = 0;
        int saved2 = 0;
        int ip = from;
        if (ip == prefixLowest) {
            ip++;
        }
        long first = state.indexOf(ip);
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
            int step = 1;
            int nextStep = ip + STEP_INCREMENT;
            int ip1 = ip + step;
            if (ip1 > limit) {
                break;
            }
            int hashLong0 = ZstdMatchState.hashAt(source, ip, longBits, 8);
            long indexLong0 = hashLong.getIndex(hashLong0);
            int hashLong1 = 0;
            long indexLong1 = 0;
            long current = 0;
            int matchLength = 0;
            int offset = 0;
            int outcome = 0;
            long indexSmall0 = 0;
            do {
                int hashSmall0 = ZstdMatchState.hashAt(source, ip, smallBits, minMatch);
                indexSmall0 = hashSmall.getIndex(hashSmall0);
                current = state.indexOf(ip);
                hashLong.setIndex(hashLong0, current);
                hashSmall.setIndex(hashSmall0, current);
                if (repeat1 > 0 && ZstdMatchState.read32(source, ip + 1 - repeat1)
                        == ZstdMatchState.read32(source, ip + 1)) {
                    matchLength = ZstdMatchState.count(source, ip + 1 + 4, ip + 1 + 4 - repeat1, end) + 4;
                    ip++;
                    store.storeSequence(ip - anchor, source, anchor, 1, matchLength);
                    outcome = 1;
                    break;
                }
                hashLong1 = ZstdMatchState.hashAt(source, ip1, longBits, 8);
                if (indexLong0 >= prefixLowestIndex) {
                    int matchLong0 = state.positionOf(indexLong0);
                    if (ZstdMatchState.read64(source, matchLong0) == ZstdMatchState.read64(source, ip)) {
                        matchLength = ZstdMatchState.count(source, ip + 8, matchLong0 + 8, end) + 8;
                        offset = ip - matchLong0;
                        while (ip > anchor && matchLong0 > prefixLowest
                                && source[ip - 1] == source[matchLong0 - 1]) {
                            ip--;
                            matchLong0--;
                            matchLength++;
                        }
                        outcome = 2;
                        break;
                    }
                }
                indexLong1 = hashLong.getIndex(hashLong1);
                if (indexSmall0 >= prefixLowestIndex && ZstdMatchState.read32(source, state.positionOf(indexSmall0))
                        == ZstdMatchState.read32(source, ip)) {
                    outcome = 3;
                    break;
                }
                if (ip1 >= nextStep) {
                    step++;
                    nextStep += STEP_INCREMENT;
                }
                ip = ip1;
                ip1 += step;
                hashLong0 = hashLong1;
                indexLong0 = indexLong1;
            } while (ip1 <= limit);
            if (outcome == 0) {
                break;
            }
            if (outcome == 3) {
                int matchSmall0 = state.positionOf(indexSmall0);
                matchLength = ZstdMatchState.count(source, ip + 4, matchSmall0 + 4, end) + 4;
                offset = ip - matchSmall0;
                if (indexLong1 > prefixLowestIndex) {
                    int matchLong1 = state.positionOf(indexLong1);
                    if (ZstdMatchState.read64(source, matchLong1) == ZstdMatchState.read64(source, ip1)) {
                        int longLength = ZstdMatchState.count(source, ip1 + 8, matchLong1 + 8, end) + 8;
                        if (longLength > matchLength) {
                            ip = ip1;
                            matchLength = longLength;
                            offset = ip - matchLong1;
                            matchSmall0 = matchLong1;
                        }
                    }
                }
                while (ip > anchor && matchSmall0 > prefixLowest && source[ip - 1] == source[matchSmall0 - 1]) {
                    ip--;
                    matchSmall0--;
                    matchLength++;
                }
            }
            if (outcome != 1) {
                repeat2 = repeat1;
                repeat1 = offset;
                if (step < 4) {
                    hashLong.setIndex(hashLong1, state.indexOf(ip1));
                }
                store.storeSequence(ip - anchor, source, anchor, offset + ZstdSequenceStore.REPEAT_COUNT,
                        matchLength);
            }
            ip += matchLength;
            anchor = ip;
            if (ip <= limit) {
                long toInsert = current + 2;
                int insertAt = state.positionOf(toInsert);
                hashLong.setIndex(ZstdMatchState.hashAt(source, insertAt, longBits, 8), toInsert);
                hashLong.setIndex(ZstdMatchState.hashAt(source, ip - 2, longBits, 8), state.indexOf(ip - 2));
                hashSmall.setIndex(ZstdMatchState.hashAt(source, insertAt, smallBits, minMatch), toInsert);
                hashSmall.setIndex(ZstdMatchState.hashAt(source, ip - 1, smallBits, minMatch), state.indexOf(ip - 1));
                while (ip <= limit && repeat2 > 0
                        && ZstdMatchState.read32(source, ip) == ZstdMatchState.read32(source, ip - repeat2)) {
                    int repeatLength = ZstdMatchState.count(source, ip + 4, ip + 4 - repeat2, end) + 4;
                    int swapped = repeat2;
                    repeat2 = repeat1;
                    repeat1 = swapped;
                    hashSmall.setIndex(ZstdMatchState.hashAt(source, ip, smallBits, minMatch), state.indexOf(ip));
                    hashLong.setIndex(ZstdMatchState.hashAt(source, ip, longBits, 8), state.indexOf(ip));
                    store.storeSequence(0, source, anchor, 1, repeatLength);
                    ip += repeatLength;
                    anchor = ip;
                }
            }
        }
        saved2 = saved1 != 0 && repeat1 != 0 ? saved1 : saved2;
        repeats[0] = repeat1 != 0 ? repeat1 : saved1;
        repeats[1] = repeat2 != 0 ? repeat2 : saved2;
        return end - anchor;
    }

    static int compressBlockWithDictionary(ZstdMatchState state, ZstdSequenceStore store, int[] repeats, int from,
                                           int size) {
        ZstdCompressionParameters parameters = state.parameters;
        int minMatch = minMatchOf(state);
        ZstdIndexTable hashLong = state.hashTable;
        int longBits = parameters.hashLog;
        ZstdIndexTable hashSmall = state.chainTable;
        int smallBits = parameters.chainLog;
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        int ip = from;
        int anchor = from;
        int end = from + size;
        int limit = end - 8;
        long endIndex = state.indexOf(from) + size;
        long lowLimit = state.lowestMatchIndex(endIndex, parameters.windowLog);
        long dictionaryStartIndex = lowLimit;
        long dictionaryLimit = state.dictionaryLimit;
        long prefixStartIndex = dictionaryLimit > lowLimit ? dictionaryLimit : lowLimit;
        int prefixStart = state.positionOf(prefixStartIndex);
        int dictionaryStart = state.dictionaryPositionOf(dictionaryStartIndex);
        int dictionaryEnd = state.dictionaryPositionOf(prefixStartIndex);
        int repeat1 = repeats[0];
        int repeat2 = repeats[1];
        if (prefixStartIndex == dictionaryStartIndex) {
            return compressBlock(state, store, repeats, from, size);
        }
        while (ip < limit) {
            int smallHash = ZstdMatchState.hashAt(source, ip, smallBits, minMatch);
            long matchIndex = hashSmall.getIndex(smallHash);
            int longHash = ZstdMatchState.hashAt(source, ip, longBits, 8);
            long matchLongIndex = hashLong.getIndex(longHash);
            long current = state.indexOf(ip);
            long repeatIndex = (current + 1 - repeat1) & 0xFFFFFFFFL;
            int matchLength;
            hashSmall.setIndex(smallHash, current);
            hashLong.setIndex(longHash, current);
            boolean isRepeatInDictionary = repeatIndex < prefixStartIndex;
            if (ZstdMatchState.isOutsideOverlap(prefixStartIndex, repeatIndex)
                    && (repeat1 & 0xFFFFFFFFL) <= current + 1 - dictionaryStartIndex
                    && ZstdMatchState.read32(isRepeatInDictionary ? dictionary : source,
                    isRepeatInDictionary ? state.dictionaryPositionOf(repeatIndex) : state.positionOf(repeatIndex))
                    == ZstdMatchState.read32(source, ip + 1)) {
                byte[] repeatArray = isRepeatInDictionary ? dictionary : source;
                int repeatMatch = isRepeatInDictionary ? state.dictionaryPositionOf(repeatIndex)
                        : state.positionOf(repeatIndex);
                int repeatEnd = isRepeatInDictionary ? dictionaryEnd : end;
                matchLength = ZstdMatchState.countTwoSegments(source, ip + 1 + 4, repeatArray, repeatMatch + 4, end,
                        repeatEnd, prefixStart) + 4;
                ip++;
                store.storeSequence(ip - anchor, source, anchor, 1, matchLength);
            } else {
                boolean isLongInDictionary = matchLongIndex < prefixStartIndex;
                byte[] longArray = isLongInDictionary ? dictionary : source;
                int matchLong = isLongInDictionary ? state.dictionaryPositionOf(matchLongIndex)
                        : state.positionOf(matchLongIndex);
                boolean isShortInDictionary = matchIndex < prefixStartIndex;
                byte[] shortArray = isShortInDictionary ? dictionary : source;
                int match = isShortInDictionary ? state.dictionaryPositionOf(matchIndex) : state.positionOf(matchIndex);
                if (matchLongIndex > dictionaryStartIndex
                        && ZstdMatchState.read64(longArray, matchLong) == ZstdMatchState.read64(source, ip)) {
                    int matchEnd = isLongInDictionary ? dictionaryEnd : end;
                    int lowMatch = isLongInDictionary ? dictionaryStart : prefixStart;
                    matchLength = ZstdMatchState.countTwoSegments(source, ip + 8, longArray, matchLong + 8, end,
                            matchEnd, prefixStart) + 8;
                    int offset = (int) (current - matchLongIndex);
                    while (ip > anchor && matchLong > lowMatch && source[ip - 1] == longArray[matchLong - 1]) {
                        ip--;
                        matchLong--;
                        matchLength++;
                    }
                    repeat2 = repeat1;
                    repeat1 = offset;
                    store.storeSequence(ip - anchor, source, anchor, offset + ZstdSequenceStore.REPEAT_COUNT,
                            matchLength);
                } else if (matchIndex > dictionaryStartIndex
                        && ZstdMatchState.read32(shortArray, match) == ZstdMatchState.read32(source, ip)) {
                    int hash3 = ZstdMatchState.hashAt(source, ip + 1, longBits, 8);
                    long matchIndex3 = hashLong.getIndex(hash3);
                    boolean isThirdInDictionary = matchIndex3 < prefixStartIndex;
                    byte[] thirdArray = isThirdInDictionary ? dictionary : source;
                    int match3 = isThirdInDictionary ? state.dictionaryPositionOf(matchIndex3)
                            : state.positionOf(matchIndex3);
                    int offset;
                    hashLong.setIndex(hash3, current + 1);
                    if (matchIndex3 > dictionaryStartIndex
                            && ZstdMatchState.read64(thirdArray, match3) == ZstdMatchState.read64(source, ip + 1)) {
                        int matchEnd = isThirdInDictionary ? dictionaryEnd : end;
                        int lowMatch = isThirdInDictionary ? dictionaryStart : prefixStart;
                        matchLength = ZstdMatchState.countTwoSegments(source, ip + 9, thirdArray, match3 + 8, end,
                                matchEnd, prefixStart) + 8;
                        ip++;
                        offset = (int) (current + 1 - matchIndex3);
                        while (ip > anchor && match3 > lowMatch && source[ip - 1] == thirdArray[match3 - 1]) {
                            ip--;
                            match3--;
                            matchLength++;
                        }
                    } else {
                        int matchEnd = isShortInDictionary ? dictionaryEnd : end;
                        int lowMatch = isShortInDictionary ? dictionaryStart : prefixStart;
                        matchLength = ZstdMatchState.countTwoSegments(source, ip + 4, shortArray, match + 4, end,
                                matchEnd, prefixStart) + 4;
                        offset = (int) (current - matchIndex);
                        while (ip > anchor && match > lowMatch && source[ip - 1] == shortArray[match - 1]) {
                            ip--;
                            match--;
                            matchLength++;
                        }
                    }
                    repeat2 = repeat1;
                    repeat1 = offset;
                    store.storeSequence(ip - anchor, source, anchor, offset + ZstdSequenceStore.REPEAT_COUNT,
                            matchLength);
                } else {
                    ip += ((ip - anchor) >> ZstdMatchState.SEARCH_STRENGTH) + 1;
                    continue;
                }
            }
            ip += matchLength;
            anchor = ip;
            if (ip <= limit) {
                long toInsert = current + 2;
                int insertAt = state.positionOf(toInsert);
                hashLong.setIndex(ZstdMatchState.hashAt(source, insertAt, longBits, 8), toInsert);
                hashLong.setIndex(ZstdMatchState.hashAt(source, ip - 2, longBits, 8), state.indexOf(ip - 2));
                hashSmall.setIndex(ZstdMatchState.hashAt(source, insertAt, smallBits, minMatch), toInsert);
                hashSmall.setIndex(ZstdMatchState.hashAt(source, ip - 1, smallBits, minMatch), state.indexOf(ip - 1));
                while (ip <= limit) {
                    long current2 = state.indexOf(ip);
                    long repeatIndex2 = (current2 - repeat2) & 0xFFFFFFFFL;
                    boolean isInDictionary = repeatIndex2 < prefixStartIndex;
                    byte[] repeatArray = isInDictionary ? dictionary : source;
                    int repeatMatch2 = isInDictionary ? state.dictionaryPositionOf(repeatIndex2)
                            : state.positionOf(repeatIndex2);
                    if (ZstdMatchState.isOutsideOverlap(prefixStartIndex, repeatIndex2)
                            && (repeat2 & 0xFFFFFFFFL) <= current2 - dictionaryStartIndex
                            && ZstdMatchState.read32(repeatArray, repeatMatch2) == ZstdMatchState.read32(source, ip)) {
                        int repeatEnd2 = isInDictionary ? dictionaryEnd : end;
                        int repeatLength2 = ZstdMatchState.countTwoSegments(source, ip + 4, repeatArray,
                                repeatMatch2 + 4, end, repeatEnd2, prefixStart) + 4;
                        int swapped = repeat2;
                        repeat2 = repeat1;
                        repeat1 = swapped;
                        store.storeSequence(0, source, anchor, 1, repeatLength2);
                        hashSmall.setIndex(ZstdMatchState.hashAt(source, ip, smallBits, minMatch), current2);
                        hashLong.setIndex(ZstdMatchState.hashAt(source, ip, longBits, 8), current2);
                        ip += repeatLength2;
                        anchor = ip;
                        continue;
                    }
                    break;
                }
            }
        }
        repeats[0] = repeat1;
        repeats[1] = repeat2;
        return end - anchor;
    }
}
