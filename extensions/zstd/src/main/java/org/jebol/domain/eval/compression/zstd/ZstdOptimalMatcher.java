package org.jebol.domain.eval.compression.zstd;

final class ZstdOptimalMatcher {

    private static final int LITERAL_FREQUENCY_ADD = 2;
    private static final int MAX_PRICE = 1 << 30;
    private static final int PREDEFINED_THRESHOLD = 8;
    private static final int BIT_COST_ACCURACY = 8;
    private static final int BIT_COST_MULTIPLIER = 1 << BIT_COST_ACCURACY;
    private static final int HASH_LOG3_LIMIT = 1 << 18;
    private static final int[] BASE_LITERAL_LENGTH_FREQUENCIES = {
            4, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1};
    private static final int[] BASE_OFFSET_CODE_FREQUENCIES = {
            6, 2, 1, 1, 2, 3, 4, 4, 4, 3, 2, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

    private final ZstdMatchState state;
    private final ZstdOptimalState optimal;
    private long nextToUpdate3;
    private ZstdRawSequenceStore ldmStore;
    private long ldmStart;
    private long ldmEnd;
    private int ldmOffset;

    ZstdOptimalMatcher(ZstdMatchState state) {
        this.state = state;
        this.optimal = state.optimal;
    }

    private static long mask32(long value) {
        return value & 0xFFFFFFFFL;
    }

    private static int highBit(long value) {
        return 63 - Long.numberOfLeadingZeros(value & 0xFFFFFFFFL);
    }

    private static int bitWeight(int stat) {
        return highBit((stat & 0xFFFFFFFFL) + 1) * BIT_COST_MULTIPLIER;
    }

    private static int fractionalWeight(int rawStat) {
        long stat = (rawStat & 0xFFFFFFFFL) + 1;
        int high = highBit(stat);
        int whole = high * BIT_COST_MULTIPLIER;
        int fraction = (int) (((stat << BIT_COST_ACCURACY) & 0xFFFFFFFFL) >>> high);
        return whole + fraction;
    }

    private static int weight(int stat, int optLevel) {
        return optLevel != 0 ? fractionalWeight(stat) : bitWeight(stat);
    }

    private void setBasePrices(int optLevel) {
        optimal.literalSumBasePrice = weight(optimal.literalSum, optLevel);
        optimal.literalLengthSumBasePrice = weight(optimal.literalLengthSum, optLevel);
        optimal.matchLengthSumBasePrice = weight(optimal.matchLengthSum, optLevel);
        optimal.offsetCodeSumBasePrice = weight(optimal.offsetCodeSum, optLevel);
    }

    private static int sum(int[] table, int count) {
        int total = 0;
        for (int at = 0; at < count; at++) {
            total += table[at];
        }
        return total;
    }

    private static int downscaleStats(int[] table, int lastIndex, int shift, boolean isBaseOne) {
        int total = 0;
        for (int at = 0; at < lastIndex + 1; at++) {
            int base = isBaseOne ? 1 : (table[at] != 0 ? 1 : 0);
            int stat = base + (table[at] >>> shift);
            total += stat;
            table[at] = stat;
        }
        return total;
    }

    private static int scaleStats(int[] table, int lastIndex, int logTarget) {
        int previousSum = sum(table, lastIndex + 1);
        int factor = previousSum >>> logTarget;
        if (Integer.compareUnsigned(factor, 1) <= 0) {
            return previousSum;
        }
        return downscaleStats(table, lastIndex, highBit(factor), true);
    }

    private void rescaleFrequencies(byte[] source, int from, int size, int optLevel) {
        optimal.priceType = ZstdOptimalState.PRICE_DYNAMIC;
        if (optimal.literalLengthSum == 0) {
            if (size <= PREDEFINED_THRESHOLD) {
                optimal.priceType = ZstdOptimalState.PRICE_PREDEFINED;
            }
            ZstdEntropyTables costs = optimal.symbolCosts;
            if (costs.huffmanRepeat == ZstdHuffmanTable.REPEAT_VALID) {
                optimal.priceType = ZstdOptimalState.PRICE_DYNAMIC;
                optimal.literalSum = 0;
                for (int literal = 0; literal <= 255; literal++) {
                    int bitCost = literal > costs.huffman.maxSymbolValue ? 0 : costs.huffman.bits[literal];
                    optimal.literalFrequencies[literal] = bitCost != 0 ? 1 << (11 - bitCost) : 1;
                    optimal.literalSum += optimal.literalFrequencies[literal];
                }
                optimal.literalLengthSum = fillFromTable(optimal.literalLengthFrequencies,
                        ZstdEntropyTables.MAX_LITERAL_LENGTH, costs.literalLengths);
                optimal.matchLengthSum = fillFromTable(optimal.matchLengthFrequencies,
                        ZstdEntropyTables.MAX_MATCH_LENGTH, costs.matchLengths);
                optimal.offsetCodeSum = fillFromTable(optimal.offsetCodeFrequencies, ZstdEntropyTables.MAX_OFFSET,
                        costs.offsets);
            } else {
                int[] max = {255};
                ZstdHistogram.countSimply(optimal.literalFrequencies, max, source, from, size);
                optimal.literalSum = downscaleStats(optimal.literalFrequencies, 255, 8, false);
                System.arraycopy(BASE_LITERAL_LENGTH_FREQUENCIES, 0, optimal.literalLengthFrequencies, 0,
                        BASE_LITERAL_LENGTH_FREQUENCIES.length);
                optimal.literalLengthSum = sum(BASE_LITERAL_LENGTH_FREQUENCIES, BASE_LITERAL_LENGTH_FREQUENCIES.length);
                java.util.Arrays.fill(optimal.matchLengthFrequencies, 1);
                optimal.matchLengthSum = ZstdEntropyTables.MAX_MATCH_LENGTH + 1;
                System.arraycopy(BASE_OFFSET_CODE_FREQUENCIES, 0, optimal.offsetCodeFrequencies, 0,
                        BASE_OFFSET_CODE_FREQUENCIES.length);
                optimal.offsetCodeSum = sum(BASE_OFFSET_CODE_FREQUENCIES, BASE_OFFSET_CODE_FREQUENCIES.length);
            }
        } else {
            optimal.literalSum = scaleStats(optimal.literalFrequencies, 255, 12);
            optimal.literalLengthSum = scaleStats(optimal.literalLengthFrequencies,
                    ZstdEntropyTables.MAX_LITERAL_LENGTH, 11);
            optimal.matchLengthSum = scaleStats(optimal.matchLengthFrequencies, ZstdEntropyTables.MAX_MATCH_LENGTH,
                    11);
            optimal.offsetCodeSum = scaleStats(optimal.offsetCodeFrequencies, ZstdEntropyTables.MAX_OFFSET, 11);
        }
        setBasePrices(optLevel);
    }

    private static int fillFromTable(int[] frequencies, int max, ZstdFseEncoding table) {
        int total = 0;
        for (int symbol = 0; symbol <= max; symbol++) {
            int bitCost = table.maxBitsOf(symbol);
            frequencies[symbol] = bitCost != 0 ? 1 << (10 - bitCost) : 1;
            total += frequencies[symbol];
        }
        return total;
    }

    private int literalsCost(byte[] source, int from, int length, int optLevel) {
        if (length == 0) {
            return 0;
        }
        if (optimal.priceType == ZstdOptimalState.PRICE_PREDEFINED) {
            return (length * 6) * BIT_COST_MULTIPLIER;
        }
        int price = optimal.literalSumBasePrice * length;
        int literalPriceMax = optimal.literalSumBasePrice - BIT_COST_MULTIPLIER;
        for (int at = 0; at < length; at++) {
            int literalPrice = weight(optimal.literalFrequencies[source[from + at] & 0xFF], optLevel);
            if (Integer.compareUnsigned(literalPrice, literalPriceMax) > 0) {
                literalPrice = literalPriceMax;
            }
            price -= literalPrice;
        }
        return price;
    }

    private int literalLengthPrice(int literalLength, int optLevel) {
        if (optimal.priceType == ZstdOptimalState.PRICE_PREDEFINED) {
            return weight(literalLength, optLevel);
        }
        if (literalLength == ZstdCompressionContext.BLOCK_SIZE_MAX) {
            return BIT_COST_MULTIPLIER + literalLengthPrice(ZstdCompressionContext.BLOCK_SIZE_MAX - 1, optLevel);
        }
        int code = ZstdSequenceStore.literalLengthCode(literalLength);
        return ZstdSequenceTable.LITERAL_LENGTH_BITS[code] * BIT_COST_MULTIPLIER + optimal.literalLengthSumBasePrice
                - weight(optimal.literalLengthFrequencies[code], optLevel);
    }

    private int matchPrice(int offBase, int matchLength, int optLevel) {
        int offsetCode = highBit(offBase);
        int matchLengthBase = matchLength - ZstdSequenceStore.MIN_MATCH;
        if (optimal.priceType == ZstdOptimalState.PRICE_PREDEFINED) {
            return weight(matchLengthBase, optLevel) + (16 + offsetCode) * BIT_COST_MULTIPLIER;
        }
        int price = offsetCode * BIT_COST_MULTIPLIER
                + (optimal.offsetCodeSumBasePrice - weight(optimal.offsetCodeFrequencies[offsetCode], optLevel));
        if (optLevel < 2 && offsetCode >= 20) {
            price += (offsetCode - 19) * 2 * BIT_COST_MULTIPLIER;
        }
        int code = ZstdSequenceStore.matchLengthCode(matchLengthBase);
        price += ZstdSequenceTable.MATCH_LENGTH_BITS[code] * BIT_COST_MULTIPLIER
                + (optimal.matchLengthSumBasePrice - weight(optimal.matchLengthFrequencies[code], optLevel));
        price += BIT_COST_MULTIPLIER / 5;
        return price;
    }

    private void updateStats(int literalLength, byte[] source, int literals, int offBase, int matchLength) {
        for (int at = 0; at < literalLength; at++) {
            optimal.literalFrequencies[source[literals + at] & 0xFF] += LITERAL_FREQUENCY_ADD;
        }
        optimal.literalSum += literalLength * LITERAL_FREQUENCY_ADD;
        optimal.literalLengthFrequencies[ZstdSequenceStore.literalLengthCode(literalLength)]++;
        optimal.literalLengthSum++;
        optimal.offsetCodeFrequencies[highBit(offBase)]++;
        optimal.offsetCodeSum++;
        optimal.matchLengthFrequencies[ZstdSequenceStore.matchLengthCode(matchLength - ZstdSequenceStore.MIN_MATCH)]++;
        optimal.matchLengthSum++;
    }

    private static int readMinMatch(byte[] buffer, int at, int length) {
        int value = ZstdMatchState.read32(buffer, at);
        return length == 3 ? value << 8 : value;
    }

    private long insertAndFindFirstIndexHash3(int input) {
        ZstdIndexTable table = state.hashTable3;
        int hashLog3 = state.hashLog3;
        byte[] source = state.baseArray;
        long index = nextToUpdate3;
        long target = state.indexOf(input);
        int hash3 = ZstdMatchState.hash3At(source, input, hashLog3);
        while (index < target) {
            table.setIndex(ZstdMatchState.hash3At(source, state.positionOf(index), hashLog3), index);
            index++;
        }
        nextToUpdate3 = target;
        return table.getIndex(hash3);
    }

    private int insertTreeNode(int input, int inputEnd, long target, int minMatch, boolean isExtended) {
        ZstdCompressionParameters parameters = state.parameters;
        ZstdIndexTable hashTable = state.hashTable;
        int hashLog = parameters.hashLog;
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        int hash = ZstdMatchState.hashAt(source, input, hashLog, minMatch);
        ZstdIndexTable tree = state.chainTable;
        long treeMask = (1L << (parameters.chainLog - 1)) - 1;
        long matchIndex = hashTable.getIndex(hash);
        long commonSmaller = 0;
        long commonLarger = 0;
        long dictionaryLimit = state.dictionaryLimit;
        int dictionaryEnd = state.dictionaryPositionOf(dictionaryLimit);
        int prefixStart = state.positionOf(dictionaryLimit);
        long current = state.indexOf(input);
        long treeLow = treeMask >= current ? 0 : current - treeMask;
        int smaller = 2 * (int) (current & treeMask);
        int larger = smaller + 1;
        boolean isSmallerDummy = false;
        boolean isLargerDummy = false;
        long windowLow = state.lowestMatchIndex(target, parameters.windowLog);
        long matchEndIndex = current + 8 + 1;
        long bestLength = 8;
        int compares = 1 << parameters.searchLog;
        hashTable.setIndex(hash, current);
        for (; compares != 0 && matchIndex >= windowLow; --compares) {
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
                bestLength = length;
                if (length > mask32(matchEndIndex - matchIndex)) {
                    matchEndIndex = matchIndex + length;
                }
            }
            if (input + length == inputEnd) {
                break;
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
        long positions = 0;
        if (bestLength > 384) {
            positions = Math.min(192, bestLength - 384);
        }
        return (int) Math.max(positions, mask32(matchEndIndex - (current + 8)));
    }

    void updateTree(int input, int inputEnd, int minMatch, boolean isExtended) {
        long target = state.indexOf(input);
        long index = state.nextToUpdate;
        while (index < target) {
            int forward = insertTreeNode(state.positionOf(index), inputEnd, target, minMatch, isExtended);
            index += forward & 0xFFFFFFFFL;
        }
        state.nextToUpdate = target;
    }

    private int getAllMatches(int input, int inputLimit, int[] repeats, int repeatsAt, int isLiteralLengthZero,
                              int lengthToBeat, int minMatchSearch, boolean isExtended) {
        if (input < state.positionOf(state.nextToUpdate)) {
            return 0;
        }
        updateTree(input, inputLimit, minMatchSearch, isExtended);
        ZstdCompressionParameters parameters = state.parameters;
        int sufficientLength = Math.min(parameters.targetLength, ZstdOptimalState.OPT_NUMBER - 1);
        byte[] source = state.baseArray;
        byte[] dictionary = state.dictionaryArray;
        long current = state.indexOf(input);
        int hashLog = parameters.hashLog;
        int minMatch = minMatchSearch == 3 ? 3 : 4;
        ZstdIndexTable hashTable = state.hashTable;
        int hash = ZstdMatchState.hashAt(source, input, hashLog, minMatchSearch);
        long matchIndex = hashTable.getIndex(hash);
        ZstdIndexTable tree = state.chainTable;
        long treeMask = (1L << (parameters.chainLog - 1)) - 1;
        long commonSmaller = 0;
        long commonLarger = 0;
        long dictionaryLimit = state.dictionaryLimit;
        int dictionaryEnd = state.dictionaryPositionOf(dictionaryLimit);
        int prefixStart = state.positionOf(dictionaryLimit);
        long treeLow = treeMask >= current ? 0 : current - treeMask;
        long windowLow = state.lowestMatchIndex(current, parameters.windowLog);
        long matchLow = windowLow != 0 ? windowLow : 1;
        int smaller = 2 * (int) (current & treeMask);
        int larger = smaller + 1;
        boolean isSmallerDummy = false;
        boolean isLargerDummy = false;
        long matchEndIndex = current + 8 + 1;
        int matchCount = 0;
        int compares = 1 << parameters.searchLog;
        long bestLength = lengthToBeat - 1;
        int[] offsets = optimal.matchOffsets;
        int[] lengths = optimal.matchLengths;
        int lastRepeat = ZstdSequenceStore.REPEAT_COUNT + isLiteralLengthZero;
        for (int repeatCode = isLiteralLengthZero; repeatCode < lastRepeat; repeatCode++) {
            long repeatOffset = repeatCode == ZstdSequenceStore.REPEAT_COUNT
                    ? mask32(repeats[repeatsAt] - 1L) : repeats[repeatsAt + repeatCode] & 0xFFFFFFFFL;
            long repeatIndex = mask32(current - repeatOffset);
            long repeatLength = 0;
            if (mask32(repeatOffset - 1) < mask32(current - dictionaryLimit)) {
                if (repeatIndex >= windowLow && readMinMatch(source, input, minMatch)
                        == readMinMatch(source, input - (int) repeatOffset, minMatch)) {
                    repeatLength = ZstdMatchState.count(source, input + minMatch,
                            input + minMatch - (int) repeatOffset, inputLimit) + minMatch;
                }
            } else if (isExtended) {
                int repeatMatch = state.dictionaryPositionOf(repeatIndex);
                if (mask32(repeatOffset - 1) < mask32(current - windowLow)
                        && ZstdMatchState.isOutsideOverlap(dictionaryLimit, repeatIndex)
                        && readMinMatch(source, input, minMatch) == readMinMatch(dictionary, repeatMatch, minMatch)) {
                    repeatLength = ZstdMatchState.countTwoSegments(source, input + minMatch, dictionary,
                            repeatMatch + minMatch, inputLimit, dictionaryEnd, prefixStart) + minMatch;
                }
            }
            if (repeatLength > bestLength) {
                bestLength = repeatLength;
                offsets[matchCount] = repeatCode - isLiteralLengthZero + 1;
                lengths[matchCount] = (int) repeatLength;
                matchCount++;
                if (repeatLength > sufficientLength || input + repeatLength == inputLimit) {
                    return matchCount;
                }
            }
        }
        if (minMatchSearch == 3 && bestLength < minMatchSearch) {
            long matchIndex3 = insertAndFindFirstIndexHash3(input);
            if (matchIndex3 >= matchLow && mask32(current - matchIndex3) < HASH_LOG3_LIMIT) {
                long length;
                if (!isExtended || matchIndex3 >= dictionaryLimit) {
                    length = ZstdMatchState.count(source, input, state.positionOf(matchIndex3), inputLimit);
                } else {
                    length = ZstdMatchState.countTwoSegments(source, input, dictionary,
                            state.dictionaryPositionOf(matchIndex3), inputLimit, dictionaryEnd, prefixStart);
                }
                if (length >= minMatchSearch) {
                    bestLength = length;
                    offsets[0] = (int) mask32(current - matchIndex3) + ZstdSequenceStore.REPEAT_COUNT;
                    lengths[0] = (int) length;
                    matchCount = 1;
                    if (length > sufficientLength || input + length == inputLimit) {
                        state.nextToUpdate = current + 1;
                        return 1;
                    }
                }
            }
        }
        hashTable.setIndex(hash, current);
        for (; compares != 0 && matchIndex >= matchLow; --compares) {
            int next = 2 * (int) (matchIndex & treeMask);
            long length = Math.min(commonSmaller, commonLarger);
            byte[] matchArray;
            int match;
            if (!isExtended || matchIndex + length >= dictionaryLimit) {
                matchArray = source;
                match = state.positionOf(matchIndex);
                length += ZstdMatchState.count(source, input + (int) length, match + (int) length, inputLimit);
            } else {
                matchArray = dictionary;
                match = state.dictionaryPositionOf(matchIndex);
                length += ZstdMatchState.countTwoSegments(source, input + (int) length, dictionary,
                        match + (int) length, inputLimit, dictionaryEnd, prefixStart);
                if (matchIndex + length >= dictionaryLimit) {
                    matchArray = source;
                    match = state.positionOf(matchIndex);
                }
            }
            if (length > bestLength) {
                if (length > mask32(matchEndIndex - matchIndex)) {
                    matchEndIndex = matchIndex + length;
                }
                bestLength = length;
                offsets[matchCount] = (int) mask32(current - matchIndex) + ZstdSequenceStore.REPEAT_COUNT;
                lengths[matchCount] = (int) length;
                matchCount++;
                if (length > ZstdOptimalState.OPT_NUMBER || input + length == inputLimit) {
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
        return matchCount;
    }

    private void nextLdmMatch(long currentPosition, long remaining) {
        ZstdRawSequenceStore store = ldmStore;
        if (store.size == 0 || store.position >= store.size) {
            ldmStart = 0xFFFFFFFFL;
            ldmEnd = 0xFFFFFFFFL;
            return;
        }
        long literalLength = store.literalLengths[store.position] & 0xFFFFFFFFL;
        long matchLength = store.matchLengths[store.position] & 0xFFFFFFFFL;
        long blockEnd = mask32(currentPosition + remaining);
        long literalsRemaining = store.positionInSequence < literalLength ? literalLength - store.positionInSequence : 0;
        long matchRemaining = literalsRemaining == 0
                ? mask32(matchLength - (store.positionInSequence - literalLength)) : matchLength;
        if (literalsRemaining >= remaining) {
            ldmStart = 0xFFFFFFFFL;
            ldmEnd = 0xFFFFFFFFL;
            store.skipBytes(remaining);
            return;
        }
        ldmStart = mask32(currentPosition + literalsRemaining);
        ldmEnd = mask32(ldmStart + matchRemaining);
        ldmOffset = store.offsets[store.position];
        if (ldmEnd > blockEnd) {
            ldmEnd = blockEnd;
            store.skipBytes(blockEnd - currentPosition);
        } else {
            store.skipBytes(literalsRemaining + matchRemaining);
        }
    }

    private int processLdmCandidate(int matchCount, long currentPosition, long remaining, int minMatch) {
        ZstdRawSequenceStore store = ldmStore;
        if (store.size == 0 || store.position >= store.size) {
            return matchCount;
        }
        if (currentPosition >= ldmEnd) {
            if (currentPosition > ldmEnd) {
                store.skipBytes(currentPosition - ldmEnd);
            }
            nextLdmMatch(currentPosition, remaining);
        }
        long positionDifference = mask32(currentPosition - ldmStart);
        long candidateLength = mask32(ldmEnd - ldmStart - positionDifference);
        if (currentPosition < ldmStart || currentPosition >= ldmEnd || candidateLength < minMatch) {
            return matchCount;
        }
        if (matchCount == 0 || (candidateLength > (optimal.matchLengths[matchCount - 1] & 0xFFFFFFFFL)
                && matchCount < ZstdOptimalState.OPT_NUMBER)) {
            optimal.matchLengths[matchCount] = (int) candidateLength;
            optimal.matchOffsets[matchCount] = ldmOffset + ZstdSequenceStore.REPEAT_COUNT;
            return matchCount + 1;
        }
        return matchCount;
    }

    private int literalPrice(byte[] source, int at, int optLevel) {
        return literalsCost(source, at, 1, optLevel);
    }

    private int literalLengthIncrementPrice(int length, int optLevel) {
        return literalLengthPrice(length, optLevel) - literalLengthPrice(length - 1, optLevel);
    }

    private static void newRepeats(int[] into, int intoAt, int[] from, int fromAt, int offBase, boolean isZero) {
        int[] repeats = {from[fromAt], from[fromAt + 1], from[fromAt + 2]};
        ZstdSequenceStore.updateRepeats(repeats, offBase, isZero ? 1 : 0);
        into[intoAt] = repeats[0];
        into[intoAt + 1] = repeats[1];
        into[intoAt + 2] = repeats[2];
    }

    int compressBlock(ZstdSequenceStore store, int[] repeats, int from, int size, int optLevel,
                      boolean isExtended) {
        ZstdOptimalState optimalState = optimal;
        byte[] source = state.baseArray;
        int ip = from;
        int anchor = from;
        int end = from + size;
        int limit = end - 8;
        int prefixStart = state.positionOf(state.dictionaryLimit);
        ZstdCompressionParameters parameters = state.parameters;
        int sufficientLength = Math.min(parameters.targetLength, ZstdOptimalState.OPT_NUMBER - 1);
        int minMatch = parameters.minMatch == 3 ? 3 : 4;
        int minMatchSearch = Math.max(3, Math.min(parameters.minMatch, 6));
        int[] prices = optimalState.prices;
        int[] offsets = optimalState.offsets;
        int[] lengths = optimalState.lengths;
        int[] literalLengths = optimalState.literalLengths;
        int[] slotRepeats = optimalState.repeats;
        int lastPrice = 0;
        int lastOffset = 0;
        int lastLength = 0;
        int lastLiteralLength = 0;
        int[] lastRepeats = new int[3];
        nextToUpdate3 = state.nextToUpdate;
        ldmStore = state.longDistanceSequences != null ? state.longDistanceSequences.copy()
                : new ZstdRawSequenceStore(0);
        ldmStart = 0;
        ldmEnd = 0;
        ldmOffset = 0;
        nextLdmMatch(ip - from, end - ip);
        rescaleFrequencies(source, from, size, optLevel);
        if (ip == prefixStart) {
            ip++;
        }
        while (ip < limit) {
            int current;
            int lastPosition = 0;
            boolean isShortestPathNow = false;
            int literalLength = ip - anchor;
            int isZero = literalLength == 0 ? 1 : 0;
            int matchCount = getAllMatches(ip, end, repeats, 0, isZero, minMatch, minMatchSearch, isExtended);
            matchCount = processLdmCandidate(matchCount, ip - from, end - ip, minMatch);
            if (matchCount == 0) {
                ip++;
                continue;
            }
            lengths[0] = 0;
            literalLengths[0] = literalLength;
            prices[0] = literalLengthPrice(literalLength, optLevel);
            slotRepeats[0] = repeats[0];
            slotRepeats[1] = repeats[1];
            slotRepeats[2] = repeats[2];
            int maxMatchLength = optimalState.matchLengths[matchCount - 1];
            int maxOffBase = optimalState.matchOffsets[matchCount - 1];
            if (Integer.compareUnsigned(maxMatchLength, sufficientLength) > 0) {
                lastLiteralLength = 0;
                lastLength = maxMatchLength;
                lastOffset = maxOffBase;
                current = 0;
                lastPosition = maxMatchLength;
                isShortestPathNow = true;
            } else {
                int position;
                for (position = 1; position < minMatch; position++) {
                    prices[position] = MAX_PRICE;
                    lengths[position] = 0;
                    literalLengths[position] = literalLength + position;
                }
                for (int match = 0; match < matchCount; match++) {
                    int offBase = optimalState.matchOffsets[match];
                    int matchEnd = optimalState.matchLengths[match];
                    for (; position <= matchEnd; position++) {
                        int sequencePrice = prices[0] + matchPrice(offBase, position, optLevel);
                        lengths[position] = position;
                        offsets[position] = offBase;
                        literalLengths[position] = 0;
                        prices[position] = sequencePrice + literalLengthPrice(0, optLevel);
                    }
                }
                lastPosition = position - 1;
                prices[position] = MAX_PRICE;
                current = 1;
            }
            if (!isShortestPathNow) {
                for (current = 1; current <= lastPosition; current++) {
                    int input = ip + current;
                    int literalCount = literalLengths[current - 1] + 1;
                    int price = prices[current - 1] + literalPrice(source, ip + current - 1, optLevel)
                            + literalLengthIncrementPrice(literalCount, optLevel);
                    if (price <= prices[current]) {
                        int previousPrice = prices[current];
                        int previousOffset = offsets[current];
                        int previousLength = lengths[current];
                        int previousLiteralLength = literalLengths[current];
                        int previousRepeat0 = slotRepeats[3 * current];
                        int previousRepeat1 = slotRepeats[3 * current + 1];
                        int previousRepeat2 = slotRepeats[3 * current + 2];
                        optimalState.copySlot(current, current - 1);
                        literalLengths[current] = literalCount;
                        prices[current] = price;
                        if (optLevel >= 1 && previousLiteralLength == 0
                                && literalLengthIncrementPrice(1, optLevel) < 0 && ip + current < end) {
                            int withOneLiteral = previousPrice + literalPrice(source, ip + current, optLevel)
                                    + literalLengthIncrementPrice(1, optLevel);
                            int withMoreLiterals = price + literalPrice(source, ip + current, optLevel)
                                    + literalLengthIncrementPrice(literalCount + 1, optLevel);
                            if (withOneLiteral < withMoreLiterals && withOneLiteral < prices[current + 1]) {
                                int previous = current - previousLength;
                                int next = current + 1;
                                prices[next] = previousPrice;
                                offsets[next] = previousOffset;
                                lengths[next] = previousLength;
                                literalLengths[next] = previousLiteralLength;
                                slotRepeats[3 * next] = previousRepeat0;
                                slotRepeats[3 * next + 1] = previousRepeat1;
                                slotRepeats[3 * next + 2] = previousRepeat2;
                                newRepeats(slotRepeats, 3 * next, slotRepeats, 3 * previous, previousOffset,
                                        literalLengths[previous] == 0);
                                literalLengths[next] = 1;
                                prices[next] = withOneLiteral;
                                if (lastPosition < next) {
                                    lastPosition = next;
                                }
                            }
                        }
                    }
                    if (literalLengths[current] == 0) {
                        int previous = current - lengths[current];
                        newRepeats(slotRepeats, 3 * current, slotRepeats, 3 * previous, offsets[current],
                                literalLengths[previous] == 0);
                    }
                    if (input > limit) {
                        continue;
                    }
                    if (current == lastPosition) {
                        break;
                    }
                    if (optLevel == 0 && prices[current + 1] <= prices[current] + (BIT_COST_MULTIPLIER / 2)) {
                        continue;
                    }
                    int isZeroHere = literalLengths[current] == 0 ? 1 : 0;
                    int previousPrice = prices[current];
                    int basePrice = previousPrice + literalLengthPrice(0, optLevel);
                    int found = getAllMatches(input, end, slotRepeats, 3 * current, isZeroHere, minMatch,
                            minMatchSearch, isExtended);
                    found = processLdmCandidate(found, input - from, end - input, minMatch);
                    if (found == 0) {
                        continue;
                    }
                    int longest = optimalState.matchLengths[found - 1];
                    if (Integer.compareUnsigned(longest, sufficientLength) > 0
                            || current + longest >= ZstdOptimalState.OPT_NUMBER
                            || (long) ip + current + longest >= end) {
                        lastLength = longest;
                        lastOffset = optimalState.matchOffsets[found - 1];
                        lastLiteralLength = 0;
                        lastPosition = current + longest;
                        isShortestPathNow = true;
                        break;
                    }
                    for (int match = 0; match < found; match++) {
                        int offset = optimalState.matchOffsets[match];
                        int lastMatchLength = optimalState.matchLengths[match];
                        int startLength = match > 0 ? optimalState.matchLengths[match - 1] + 1 : minMatch;
                        for (int length = lastMatchLength; length >= startLength; length--) {
                            int position = current + length;
                            int price2 = basePrice + matchPrice(offset, length, optLevel);
                            if (position > lastPosition || price2 < prices[position]) {
                                while (lastPosition < position) {
                                    lastPosition++;
                                    prices[lastPosition] = MAX_PRICE;
                                    literalLengths[lastPosition] = 1;
                                }
                                lengths[position] = length;
                                offsets[position] = offset;
                                literalLengths[position] = 0;
                                prices[position] = price2;
                            } else if (optLevel == 0) {
                                break;
                            }
                        }
                    }
                    prices[lastPosition + 1] = MAX_PRICE;
                }
                if (!isShortestPathNow) {
                    lastPrice = prices[lastPosition];
                    lastOffset = offsets[lastPosition];
                    lastLength = lengths[lastPosition];
                    lastLiteralLength = literalLengths[lastPosition];
                    lastRepeats[0] = slotRepeats[3 * lastPosition];
                    lastRepeats[1] = slotRepeats[3 * lastPosition + 1];
                    lastRepeats[2] = slotRepeats[3 * lastPosition + 2];
                    current = lastPosition - lastLength;
                }
            }
            if (lastLength == 0) {
                ip += lastPosition;
                continue;
            }
            if (lastLiteralLength == 0) {
                newRepeats(repeats, 0, slotRepeats, 3 * current, lastOffset, literalLengths[current] == 0);
            } else {
                repeats[0] = lastRepeats[0];
                repeats[1] = lastRepeats[1];
                repeats[2] = lastRepeats[2];
                current -= lastLiteralLength;
            }
            int storeEnd = current + 2;
            int storeStart;
            int stretchPosition = current;
            if (lastLiteralLength > 0) {
                storeStart = storeEnd - 1;
                writeSlot(storeStart, lastPrice, lastOffset, lastLength, lastLiteralLength, lastRepeats);
            }
            writeSlot(storeEnd, lastPrice, lastOffset, lastLength, lastLiteralLength, lastRepeats);
            storeStart = storeEnd;
            for (;;) {
                int nextLiteralLength = literalLengths[stretchPosition];
                int nextLength = lengths[stretchPosition];
                literalLengths[storeStart] = nextLiteralLength;
                if (nextLength == 0) {
                    break;
                }
                storeStart--;
                optimalState.copySlot(storeStart, stretchPosition);
                stretchPosition -= nextLiteralLength + nextLength;
            }
            for (int position = storeStart; position <= storeEnd; position++) {
                int literalCount = literalLengths[position];
                int matchLength = lengths[position];
                int offBase = offsets[position];
                int advance = literalCount + matchLength;
                if (matchLength == 0) {
                    ip = anchor + literalCount;
                    continue;
                }
                updateStats(literalCount, source, anchor, offBase, matchLength);
                store.storeSequence(literalCount, source, anchor, offBase, matchLength);
                anchor += advance;
                ip = anchor;
            }
            setBasePrices(optLevel);
        }
        return end - anchor;
    }

    private void writeSlot(int at, int price, int offset, int length, int literalLength, int[] slotRepeats) {
        optimal.prices[at] = price;
        optimal.offsets[at] = offset;
        optimal.lengths[at] = length;
        optimal.literalLengths[at] = literalLength;
        optimal.repeats[3 * at] = slotRepeats[0];
        optimal.repeats[3 * at + 1] = slotRepeats[1];
        optimal.repeats[3 * at + 2] = slotRepeats[2];
    }

    int compressBlockUltra2(ZstdSequenceStore store, int[] repeats, int from, int size) {
        long current = state.indexOf(from);
        if (optimal.literalLengthSum == 0 && store.sequencesEnd == store.sequencesStart
                && state.dictionaryLimit == state.lowLimit && current == state.dictionaryLimit
                && size > PREDEFINED_THRESHOLD) {
            int[] temporaryRepeats = {repeats[0], repeats[1], repeats[2]};
            compressBlock(store, temporaryRepeats, from, size, 2, false);
            store.reset();
            state.base -= size;
            state.dictionaryLimit += size;
            state.lowLimit = state.dictionaryLimit;
            state.nextToUpdate = state.dictionaryLimit;
        }
        return compressBlock(store, repeats, from, size, 2, false);
    }
}
