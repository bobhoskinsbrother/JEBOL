package org.jebol.domain.eval.deflate;

import java.util.Arrays;

import static org.jebol.domain.eval.deflate.DeflateTables.END_OF_BLOCK;
import static org.jebol.domain.eval.deflate.DeflateTables.EXTRA_LENGTH_BITS;
import static org.jebol.domain.eval.deflate.DeflateTables.EXTRA_OFFSET_BITS;
import static org.jebol.domain.eval.deflate.DeflateTables.FIRST_LENGTH_SYMBOL;
import static org.jebol.domain.eval.deflate.DeflateTables.LENGTH_SLOT;
import static org.jebol.domain.eval.deflate.DeflateTables.LITERAL_COUNT;
import static org.jebol.domain.eval.deflate.DeflateTables.MAX_BLOCK_LENGTH;
import static org.jebol.domain.eval.deflate.DeflateTables.MAX_MATCH_LENGTH;
import static org.jebol.domain.eval.deflate.DeflateTables.MIN_MATCH_LENGTH;
import static org.jebol.domain.eval.deflate.DeflateTables.OFFSET_SLOT_BASE;
import static org.jebol.domain.eval.deflate.DeflateTables.OFFSET_SYMBOL_COUNT;
import static org.jebol.domain.eval.deflate.DeflateTables.SOFT_MAX_BLOCK_LENGTH;
import static org.jebol.domain.eval.deflate.DeflateTables.WINDOW_SIZE;

final class NearOptimalParser extends DeflateParser {

    private static final int BIT_COST = 16;

    private static final int LITERAL_NOSTAT_BITS = 13;

    private static final int LENGTH_NOSTAT_BITS = 13;

    private static final int OFFSET_NOSTAT_BITS = 10;

    private static final int MATCH_CACHE_LENGTH = SOFT_MAX_BLOCK_LENGTH * 5;

    private static final int MAX_MATCHES_PER_POSITION = MAX_MATCH_LENGTH - MIN_MATCH_LENGTH + 1;

    private static final int OFFSET_SHIFT = 9;

    private static final int LENGTH_MASK = (1 << OFFSET_SHIFT) - 1;

    private static final int UNREACHABLE = 0x80000000;

    private static final long NO_COST_YET = 0xFFFFFFFFL;

    private static final int STATIC_END_OF_BLOCK_BITS = 7;

    private static final int RARE_LITERAL_SHIFT = 11;

    private static final int OFFSET_SYMBOL_COST = 4 * BIT_COST + (907 * BIT_COST) / 1000;

    private static final int[][] DEFAULT_LITERAL_COSTS = {
        {6, 6, 22, 32, 38, 43, 48, 51, 54, 57, 59, 61, 64, 65, 67, 69, 70, 72, 73, 74, 75, 76, 77, 79, 80, 80, 81, 82, 83, 84, 85, 85, 86, 87, 88, 88, 89, 89, 90, 91, 91, 92, 92, 93, 93, 94, 95, 95, 96, 96, 96, 97, 97, 98, 98, 99, 99, 99, 100, 100, 101, 101, 101, 102, 102, 102, 103, 103, 104, 104, 104, 105, 105, 105, 105, 106, 106, 106, 107, 107, 107, 108, 108, 108, 108, 109, 109, 109, 109, 110, 110, 110, 111, 111, 111, 111, 112, 112, 112, 112, 112, 113, 113, 113, 113, 114, 114, 114, 114, 114, 115, 115, 115, 115, 115, 116, 116, 116, 116, 116, 117, 117, 117, 117, 117, 118, 118, 118, 118, 118, 118, 119, 119, 119, 119, 119, 120, 120, 120, 120, 120, 120, 121, 121, 121, 121, 121, 121, 121, 122, 122, 122, 122, 122, 122, 123, 123, 123, 123, 123, 123, 123, 124, 124, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 127, 127, 127, 128, 128, 128, 128, 128, 128, 128, 128, 128, 129, 129, 129, 129, 129, 129, 129, 129, 129, 130, 130, 130, 130, 130, 130, 130, 130, 130, 131, 131, 131, 131, 131, 131, 131, 131, 131, 131, 132, 132, 132, 132, 132, 132, 132, 132, 132, 132, 133, 133, 133, 133, 133, 133, 133, 133, 133, 133, 134, 134, 134, 134, 134, 134, 134, 134},
        {16, 16, 32, 41, 48, 53, 57, 60, 64, 66, 69, 71, 73, 75, 76, 78, 80, 81, 82, 83, 85, 86, 87, 88, 89, 90, 91, 92, 92, 93, 94, 95, 96, 96, 97, 98, 98, 99, 99, 100, 101, 101, 102, 102, 103, 103, 104, 104, 105, 105, 106, 106, 107, 107, 108, 108, 108, 109, 109, 110, 110, 110, 111, 111, 112, 112, 112, 113, 113, 113, 114, 114, 114, 115, 115, 115, 115, 116, 116, 116, 117, 117, 117, 118, 118, 118, 118, 119, 119, 119, 119, 120, 120, 120, 120, 121, 121, 121, 121, 122, 122, 122, 122, 122, 123, 123, 123, 123, 124, 124, 124, 124, 124, 125, 125, 125, 125, 125, 126, 126, 126, 126, 126, 127, 127, 127, 127, 127, 128, 128, 128, 128, 128, 128, 129, 129, 129, 129, 129, 129, 130, 130, 130, 130, 130, 130, 131, 131, 131, 131, 131, 131, 131, 132, 132, 132, 132, 132, 132, 133, 133, 133, 133, 133, 133, 133, 134, 134, 134, 134, 134, 134, 134, 134, 135, 135, 135, 135, 135, 135, 135, 135, 136, 136, 136, 136, 136, 136, 136, 136, 137, 137, 137, 137, 137, 137, 137, 137, 138, 138, 138, 138, 138, 138, 138, 138, 138, 139, 139, 139, 139, 139, 139, 139, 139, 139, 140, 140, 140, 140, 140, 140, 140, 140, 140, 141, 141, 141, 141, 141, 141, 141, 141, 141, 141, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 142, 143, 143, 143, 143, 143, 143, 143, 143, 143, 143, 144},
        {32, 32, 48, 57, 64, 69, 73, 76, 80, 82, 85, 87, 89, 91, 92, 94, 96, 97, 98, 99, 101, 102, 103, 104, 105, 106, 107, 108, 108, 109, 110, 111, 112, 112, 113, 114, 114, 115, 115, 116, 117, 117, 118, 118, 119, 119, 120, 120, 121, 121, 122, 122, 123, 123, 124, 124, 124, 125, 125, 126, 126, 126, 127, 127, 128, 128, 128, 129, 129, 129, 130, 130, 130, 131, 131, 131, 131, 132, 132, 132, 133, 133, 133, 134, 134, 134, 134, 135, 135, 135, 135, 136, 136, 136, 136, 137, 137, 137, 137, 138, 138, 138, 138, 138, 139, 139, 139, 139, 140, 140, 140, 140, 140, 141, 141, 141, 141, 141, 142, 142, 142, 142, 142, 143, 143, 143, 143, 143, 144, 144, 144, 144, 144, 144, 145, 145, 145, 145, 145, 145, 146, 146, 146, 146, 146, 146, 147, 147, 147, 147, 147, 147, 147, 148, 148, 148, 148, 148, 148, 149, 149, 149, 149, 149, 149, 149, 150, 150, 150, 150, 150, 150, 150, 150, 151, 151, 151, 151, 151, 151, 151, 151, 152, 152, 152, 152, 152, 152, 152, 152, 153, 153, 153, 153, 153, 153, 153, 153, 154, 154, 154, 154, 154, 154, 154, 154, 154, 155, 155, 155, 155, 155, 155, 155, 155, 155, 156, 156, 156, 156, 156, 156, 156, 156, 156, 157, 157, 157, 157, 157, 157, 157, 157, 157, 157, 158, 158, 158, 158, 158, 158, 158, 158, 158, 158, 158, 159, 159, 159, 159, 159, 159, 159, 159, 159, 159, 160},
    };

    private static final int[] DEFAULT_LENGTH_SYMBOL_COSTS = {109, 93, 84};

    private static final int FEW_MATCHES = 0;

    private static final int SOME_MATCHES = 1;

    private static final int MANY_MATCHES = 2;

    private final BinaryTreeMatchFinder finder = new BinaryTreeMatchFinder();

    private final MatchCache cache;

    private final int[] nodeCosts;

    private final int[] nodeItems;

    private final int lastNode;

    private final int[] literalCosts = new int[LITERAL_COUNT];

    private final int[] lengthCosts = new int[MAX_MATCH_LENGTH + 1];

    private final int[] offsetSlotCosts = new int[OFFSET_SYMBOL_COUNT];

    private final int[] savedLiteralCosts = new int[LITERAL_COUNT];

    private final int[] savedLengthCosts = new int[MAX_MATCH_LENGTH + 1];

    private final int[] savedOffsetSlotCosts = new int[OFFSET_SYMBOL_COUNT];

    private final int[] offsetSlotOfEveryOffset = new int[WINDOW_SIZE + 1];

    private final int[] previousObservations = new int[BlockSplitStatistics.OBSERVATION_TYPES];

    private int previousObservationCount;

    private final int[] newMatchLengthFrequencies = new int[MAX_MATCH_LENGTH + 1];

    private final int[] matchLengthFrequencies = new int[MAX_MATCH_LENGTH + 1];

    private final int maxOptimisationPasses;

    private final int minImprovementToContinue;

    private final int minBitsToUseANonFinalPath;

    private final int longestBlockToOptimiseAsStatic;

    private int defaultLiteralCost;

    private int defaultLengthSymbolCost;

    NearOptimalParser(BlockEncoder encoder, int inputLength, int maxSearchDepth, int niceMatchLength,
            int maxOptimisationPasses, int minImprovementToContinue, int minBitsToUseANonFinalPath,
            int longestBlockToOptimiseAsStatic) {
        super(encoder, maxSearchDepth, niceMatchLength);
        this.maxOptimisationPasses = maxOptimisationPasses;
        this.minImprovementToContinue = minImprovementToContinue;
        this.minBitsToUseANonFinalPath = minBitsToUseANonFinalPath;
        this.longestBlockToOptimiseAsStatic = longestBlockToOptimiseAsStatic;
        long cacheNeeded = (long) inputLength * (MAX_MATCHES_PER_POSITION + 1) + MAX_MATCH_LENGTH;
        cache = new MatchCache((int) Math.min(
                MATCH_CACHE_LENGTH + MAX_MATCHES_PER_POSITION + MAX_MATCH_LENGTH - 1, cacheNeeded));
        lastNode = MAX_BLOCK_LENGTH;
        int nodesNeeded = Math.min(MAX_BLOCK_LENGTH + 1, inputLength + MAX_MATCH_LENGTH + 1);
        nodeCosts = new int[nodesNeeded];
        nodeItems = new int[nodesNeeded];
        fillTheOffsetSlotOfEveryOffset();
    }

    private void fillTheOffsetSlotOfEveryOffset() {
        for (int slot = 0; slot < OFFSET_SLOT_BASE.length; slot++) {
            int first = OFFSET_SLOT_BASE[slot];
            Arrays.fill(offsetSlotOfEveryOffset, first, first + (1 << EXTRA_OFFSET_BITS[slot]), slot);
        }
    }

    @Override
    void compress(byte[] input, int length, BitWriter out) {
        int next = 0;
        int blockBegin = 0;
        int base = 0;
        int nextSlide = Math.min(length, WINDOW_SIZE);
        int cacheAt = 0;
        boolean previousBlockUsedOnlyLiterals = false;
        resetStatistics();
        do {
            int blockEnd = maxBlockEnd(blockBegin, length, SOFT_MAX_BLOCK_LENGTH);
            int previousEndBlockCheck = -1;
            boolean changeDetected = false;
            int nextObservation = next;
            int minLength = previousBlockUsedOnlyLiterals
                    ? MAX_MATCH_LENGTH + 1
                    : minMatchLengthScanning(input, blockBegin, blockEnd - blockBegin);
            while (true) {
                int remaining = length - next;
                if (next == nextSlide) {
                    finder.slideWindow();
                    base = next;
                    nextSlide = next + Math.min(remaining, WINDOW_SIZE);
                }
                int matchesFrom = cacheAt;
                int bestLength = 0;
                adjustMaxAndNiceLength(remaining);
                if (maxLength >= BinaryTreeMatchFinder.REQUIRED_BYTES) {
                    cacheAt = finder.matchesAt(input, base, next - base, maxLength, niceLength, maxSearchDepth,
                            cache, cacheAt);
                    if (cacheAt > matchesFrom) {
                        bestLength = cache.lengths[cacheAt - 1];
                    }
                }
                if (next >= nextObservation) {
                    if (bestLength >= minLength) {
                        splitStatistics.observeMatch(bestLength);
                        nextObservation = next + bestLength;
                        newMatchLengthFrequencies[bestLength]++;
                    } else {
                        splitStatistics.observeLiteral(input[next] & 0xFF);
                        nextObservation = next + 1;
                    }
                }
                cache.record(cacheAt, cacheAt - matchesFrom, input[next] & 0xFF);
                next++;
                cacheAt++;
                if (bestLength >= MIN_MATCH_LENGTH && bestLength >= niceLength) {
                    --bestLength;
                    do {
                        remaining = length - next;
                        if (next == nextSlide) {
                            finder.slideWindow();
                            base = next;
                            nextSlide = next + Math.min(remaining, WINDOW_SIZE);
                        }
                        adjustMaxAndNiceLength(remaining);
                        if (maxLength >= BinaryTreeMatchFinder.REQUIRED_BYTES) {
                            finder.skipByte(input, base, next - base, niceLength, maxSearchDepth, cache);
                        }
                        cache.record(cacheAt, 0, input[next] & 0xFF);
                        next++;
                        cacheAt++;
                    } while (--bestLength != 0);
                }
                if (next >= blockEnd || cacheAt >= MATCH_CACHE_LENGTH) {
                    break;
                }
                if (!splitStatistics.readyToCheck(blockBegin, next, length)) {
                    continue;
                }
                if (splitStatistics.endsTheBlock(next - blockBegin)) {
                    changeDetected = true;
                    break;
                }
                mergeStatistics();
                previousEndBlockCheck = next;
            }
            if (changeDetected && previousEndBlockCheck != -1) {
                int originalCacheEnd = cacheAt;
                int bytesToRewind = next - previousEndBlockCheck;
                do {
                    cacheAt--;
                    cacheAt -= cache.lengths[cacheAt];
                } while (--bytesToRewind != 0);
                int rewound = originalCacheEnd - cacheAt;
                previousBlockUsedOnlyLiterals = optimiseAndFlushBlock(out, input, blockBegin,
                        previousEndBlockCheck - blockBegin, cacheAt, blockBegin == 0, false);
                cache.moveToTheStart(cacheAt, rewound);
                cacheAt = rewound;
                saveStatistics();
                splitStatistics.clearTheOldObservations();
                Arrays.fill(matchLengthFrequencies, 0);
                blockBegin = previousEndBlockCheck;
            } else {
                mergeStatistics();
                previousBlockUsedOnlyLiterals = optimiseAndFlushBlock(out, input, blockBegin,
                        next - blockBegin, cacheAt, blockBegin == 0, next == length);
                cacheAt = 0;
                saveStatistics();
                resetStatistics();
                blockBegin = next;
            }
        } while (next != length);
    }

    private void resetStatistics() {
        splitStatistics.reset();
        Arrays.fill(newMatchLengthFrequencies, 0);
        Arrays.fill(matchLengthFrequencies, 0);
    }

    private void mergeStatistics() {
        splitStatistics.mergeNewObservations();
        for (int length = 0; length < matchLengthFrequencies.length; length++) {
            matchLengthFrequencies[length] += newMatchLengthFrequencies[length];
            newMatchLengthFrequencies[length] = 0;
        }
    }

    private void saveStatistics() {
        System.arraycopy(splitStatistics.observations, 0, previousObservations, 0, previousObservations.length);
        previousObservationCount = splitStatistics.observationCount;
    }

    private boolean optimiseAndFlushBlock(BitWriter out, byte[] input, int blockBegin, int blockLength,
            int cacheEnd, boolean firstBlock, boolean finalBlock) {
        int passesRemaining = maxOptimisationPasses;
        long bestTrueCost = NO_COST_YET;
        long trueCost;
        long staticCost = NO_COST_YET;
        chooseOnlyLiterals(input, blockBegin, blockLength);
        long onlyLiteralsCost = encoder.trueCost();
        int lastForced = Math.min(blockLength - 1 + MAX_MATCH_LENGTH, lastNode);
        for (int node = blockLength; node <= lastForced; node++) {
            nodeCosts[node] = UNREACHABLE;
        }
        if (blockLength <= longestBlockToOptimiseAsStatic) {
            saveCosts();
            setCostsFromCodeLengths(encoder.staticCodes.lengths);
            findTheCheapestPath(blockLength, cacheEnd);
            staticCost = Integer.toUnsignedLong(nodeCosts[0]) / BIT_COST + STATIC_END_OF_BLOCK_BITS;
            restoreCosts();
        }
        setInitialCosts(input, blockBegin, blockLength, firstBlock);
        do {
            findTheCheapestPath(blockLength, cacheEnd);
            trueCost = encoder.trueCost();
            if (trueCost + minImprovementToContinue > bestTrueCost) {
                break;
            }
            bestTrueCost = trueCost;
            saveCosts();
            setCostsFromCodeLengths(encoder.codes.lengths);
        } while (--passesRemaining != 0);
        boolean onlyLiterals = false;
        BlockContent content = new CheapestPath(blockLength);
        if (Math.min(onlyLiteralsCost, staticCost) < bestTrueCost) {
            if (onlyLiteralsCost < staticCost) {
                chooseOnlyLiterals(input, blockBegin, blockLength);
                setCostsFromCodeLengths(encoder.codes.lengths);
                content = new Sequences(0).holdingOnlyLiterals(blockLength);
                onlyLiterals = true;
            } else {
                setCostsFromCodeLengths(encoder.staticCodes.lengths);
                findTheCheapestPath(blockLength, cacheEnd);
            }
        } else if (trueCost >= bestTrueCost + minBitsToUseANonFinalPath) {
            restoreCosts();
            findTheCheapestPath(blockLength, cacheEnd);
            setCostsFromCodeLengths(encoder.codes.lengths);
        }
        encoder.flushBlock(out, input, blockBegin, blockLength, content, finalBlock);
        return onlyLiterals;
    }

    private void saveCosts() {
        System.arraycopy(literalCosts, 0, savedLiteralCosts, 0, literalCosts.length);
        System.arraycopy(lengthCosts, 0, savedLengthCosts, 0, lengthCosts.length);
        System.arraycopy(offsetSlotCosts, 0, savedOffsetSlotCosts, 0, offsetSlotCosts.length);
    }

    private void restoreCosts() {
        System.arraycopy(savedLiteralCosts, 0, literalCosts, 0, literalCosts.length);
        System.arraycopy(savedLengthCosts, 0, lengthCosts, 0, lengthCosts.length);
        System.arraycopy(savedOffsetSlotCosts, 0, offsetSlotCosts, 0, offsetSlotCosts.length);
    }

    private void chooseOnlyLiterals(byte[] input, int blockBegin, int blockLength) {
        encoder.resetFrequencies();
        for (int at = 0; at < blockLength; at++) {
            encoder.litlenFrequencies[input[blockBegin + at] & 0xFF]++;
        }
        encoder.litlenFrequencies[END_OF_BLOCK]++;
        encoder.makeHuffmanCodes(encoder.codes);
    }

    private void findTheCheapestPath(int blockLength, int cacheEnd) {
        int node = blockLength;
        int cacheAt = cacheEnd;
        nodeCosts[node] = 0;
        do {
            node--;
            cacheAt--;
            int matchCount = cache.lengths[cacheAt];
            int literal = cache.offsets[cacheAt];
            int bestCost = literalCosts[literal] + nodeCosts[node + 1];
            nodeItems[node] = (literal << OFFSET_SHIFT) | 1;
            if (matchCount != 0) {
                int match = cacheAt - matchCount;
                int length = MIN_MATCH_LENGTH;
                do {
                    int offset = cache.offsets[match];
                    int offsetCost = offsetSlotCosts[offsetSlotOfEveryOffset[offset]];
                    do {
                        int cost = offsetCost + lengthCosts[length] + nodeCosts[node + length];
                        if (Integer.compareUnsigned(cost, bestCost) < 0) {
                            bestCost = cost;
                            nodeItems[node] = length | (offset << OFFSET_SHIFT);
                        }
                    } while (++length <= cache.lengths[match]);
                } while (++match != cacheAt);
                cacheAt -= matchCount;
            }
            nodeCosts[node] = bestCost;
        } while (node != 0);
        encoder.resetFrequencies();
        tallyThePath(blockLength);
        encoder.makeHuffmanCodes(encoder.codes);
    }

    private void tallyThePath(int blockLength) {
        int node = 0;
        do {
            int length = nodeItems[node] & LENGTH_MASK;
            int offset = nodeItems[node] >>> OFFSET_SHIFT;
            if (length == 1) {
                encoder.litlenFrequencies[offset]++;
            } else {
                encoder.litlenFrequencies[FIRST_LENGTH_SYMBOL + LENGTH_SLOT[length]]++;
                encoder.offsetFrequencies[offsetSlotOfEveryOffset[offset]]++;
            }
            node += length;
        } while (node != blockLength);
        encoder.litlenFrequencies[END_OF_BLOCK]++;
    }

    private void setCostsFromCodeLengths(int[] lengths) {
        for (int literal = 0; literal < LITERAL_COUNT; literal++) {
            int bits = lengths[literal] != 0 ? lengths[literal] : LITERAL_NOSTAT_BITS;
            literalCosts[literal] = bits * BIT_COST;
        }
        for (int length = MIN_MATCH_LENGTH; length <= MAX_MATCH_LENGTH; length++) {
            int slot = LENGTH_SLOT[length];
            int symbol = FIRST_LENGTH_SYMBOL + slot;
            int bits = lengths[symbol] != 0 ? lengths[symbol] : LENGTH_NOSTAT_BITS;
            lengthCosts[length] = (bits + EXTRA_LENGTH_BITS[slot]) * BIT_COST;
        }
        for (int slot = 0; slot < OFFSET_SLOT_BASE.length; slot++) {
            int codeLength = lengths[HuffmanCodes.OFFSET_LENGTHS_FROM + slot];
            int bits = codeLength != 0 ? codeLength : OFFSET_NOSTAT_BITS;
            offsetSlotCosts[slot] = (bits + EXTRA_OFFSET_BITS[slot]) * BIT_COST;
        }
    }

    private void chooseDefaultLiteralAndLengthCosts(byte[] input, int blockBegin, int blockLength) {
        int literalFrequency = blockLength;
        Arrays.fill(encoder.litlenFrequencies, 0, LITERAL_COUNT, 0);
        int cutoff = literalFrequency >>> RARE_LITERAL_SHIFT;
        for (int at = 0; at < blockLength; at++) {
            encoder.litlenFrequencies[input[blockBegin + at] & 0xFF]++;
        }
        int literalsUsed = 0;
        for (int literal = 0; literal < LITERAL_COUNT; literal++) {
            if (encoder.litlenFrequencies[literal] > cutoff) {
                literalsUsed++;
            }
        }
        if (literalsUsed == 0) {
            literalsUsed = 1;
        }
        int matchFrequency = 0;
        for (int length = minMatchLengthFor(literalsUsed); length < matchLengthFrequencies.length; length++) {
            matchFrequency += matchLengthFrequencies[length];
            literalFrequency -= length * matchLengthFrequencies[length];
        }
        if (literalFrequency < 0) {
            literalFrequency = 0;
        }
        int matchiness;
        if (matchFrequency > literalFrequency) {
            matchiness = MANY_MATCHES;
        } else if (matchFrequency * 4 > literalFrequency) {
            matchiness = SOME_MATCHES;
        } else {
            matchiness = FEW_MATCHES;
        }
        defaultLiteralCost = DEFAULT_LITERAL_COSTS[matchiness][literalsUsed];
        defaultLengthSymbolCost = DEFAULT_LENGTH_SYMBOL_COSTS[matchiness];
    }

    private int defaultLengthCost(int length) {
        return defaultLengthSymbolCost + EXTRA_LENGTH_BITS[LENGTH_SLOT[length]] * BIT_COST;
    }

    private int defaultOffsetSlotCost(int slot) {
        return OFFSET_SYMBOL_COST + EXTRA_OFFSET_BITS[slot] * BIT_COST;
    }

    private void setDefaultCosts() {
        Arrays.fill(literalCosts, defaultLiteralCost);
        for (int length = MIN_MATCH_LENGTH; length <= MAX_MATCH_LENGTH; length++) {
            lengthCosts[length] = defaultLengthCost(length);
        }
        for (int slot = 0; slot < OFFSET_SLOT_BASE.length; slot++) {
            offsetSlotCosts[slot] = defaultOffsetSlotCost(slot);
        }
    }

    private int adjusted(int cost, int defaultCost, int changeAmount) {
        return switch (changeAmount) {
            case 0 -> (defaultCost + 3 * cost) / 4;
            case 1 -> (defaultCost + cost) / 2;
            case 2 -> (5 * defaultCost + 3 * cost) / 8;
            default -> (3 * defaultCost + cost) / 4;
        };
    }

    private void adjustCostsBy(int changeAmount) {
        for (int literal = 0; literal < LITERAL_COUNT; literal++) {
            literalCosts[literal] = adjusted(literalCosts[literal], defaultLiteralCost, changeAmount);
        }
        for (int length = MIN_MATCH_LENGTH; length <= MAX_MATCH_LENGTH; length++) {
            lengthCosts[length] = adjusted(lengthCosts[length], defaultLengthCost(length), changeAmount);
        }
        for (int slot = 0; slot < OFFSET_SLOT_BASE.length; slot++) {
            offsetSlotCosts[slot] = adjusted(offsetSlotCosts[slot], defaultOffsetSlotCost(slot), changeAmount);
        }
    }

    private void adjustCosts() {
        long totalDelta = 0;
        for (int type = 0; type < BlockSplitStatistics.OBSERVATION_TYPES; type++) {
            long previous = (long) previousObservations[type] * splitStatistics.observationCount;
            long current = (long) splitStatistics.observations[type] * previousObservationCount;
            totalDelta += Math.abs(previous - current);
        }
        long cutoff = (long) previousObservationCount * splitStatistics.observationCount * 200 / 512;
        if (totalDelta > 3 * cutoff) {
            setDefaultCosts();
        } else if (4 * totalDelta > 9 * cutoff) {
            adjustCostsBy(3);
        } else if (2 * totalDelta > 3 * cutoff) {
            adjustCostsBy(2);
        } else if (2 * totalDelta > cutoff) {
            adjustCostsBy(1);
        } else {
            adjustCostsBy(0);
        }
    }

    private void setInitialCosts(byte[] input, int blockBegin, int blockLength, boolean firstBlock) {
        chooseDefaultLiteralAndLengthCosts(input, blockBegin, blockLength);
        if (firstBlock) {
            setDefaultCosts();
        } else {
            adjustCosts();
        }
    }

    private final class CheapestPath implements BlockContent {

        private final int blockLength;

        CheapestPath(int blockLength) {
            this.blockLength = blockLength;
        }

        @Override
        public void writeWith(BlockEncoder writer, HuffmanCodes codes, BitWriter out, byte[] input, int begin) {
            int node = 0;
            do {
                int length = nodeItems[node] & LENGTH_MASK;
                int offset = nodeItems[node] >>> OFFSET_SHIFT;
                if (length == 1) {
                    writer.writeLiteral(out, codes, offset);
                } else {
                    writer.writeMatch(out, codes, length, offset, offsetSlotOfEveryOffset[offset]);
                }
                node += length;
            } while (node != blockLength);
        }
    }
}
