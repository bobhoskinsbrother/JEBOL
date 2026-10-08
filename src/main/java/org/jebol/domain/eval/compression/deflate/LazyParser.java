package org.jebol.domain.eval.compression.deflate;

import static org.jebol.domain.eval.compression.deflate.DeflateTables.MIN_MATCH_LENGTH;
import static org.jebol.domain.eval.compression.deflate.DeflateTables.SOFT_MAX_BLOCK_LENGTH;

final class LazyParser extends DeflateParser {

    private static final int RECALCULATION_INTERVAL = 10000;

    private static final int FARTHEST_WORTHWHILE_SHORTEST_MATCH = 8192;

    private static final int GAIN_TO_WAIT_ONE_BYTE = 2;

    private static final int GAIN_TO_WAIT_TWO_BYTES = 6;

    private final HashChainMatchFinder finder = new HashChainMatchFinder();

    private final Sequences sequences = new Sequences(GreedyParser.SEQUENCE_STORE_LENGTH);

    private final boolean looksTwoBytesAhead;

    LazyParser(BlockEncoder encoder, int maxSearchDepth, int niceMatchLength, boolean looksTwoBytesAhead) {
        super(encoder, maxSearchDepth, niceMatchLength);
        this.looksTwoBytesAhead = looksTwoBytesAhead;
    }

    @Override
    void compress(byte[] input, int length, BitWriter out) {
        int next = 0;
        do {
            int blockBegin = next;
            int blockEnd = maxBlockEnd(next, length, SOFT_MAX_BLOCK_LENGTH);
            int nextRecalculation = next + Math.min(length - next, RECALCULATION_INTERVAL);
            int sequence = 0;
            splitStatistics.reset();
            beginSequences(sequences);
            int minLength = minMatchLengthScanning(input, next, blockEnd - next);
            do {
                if (next >= nextRecalculation) {
                    minLength = minMatchLengthFromTheFrequencies();
                    nextRecalculation += Math.min(length - nextRecalculation, next - blockBegin);
                }
                adjustMaxAndNiceLength(length - next);
                int currentLength = finder.longestMatch(
                        input, next, minLength - 1, maxLength, niceLength, maxSearchDepth);
                int currentOffset = finder.offset();
                if (currentLength < minLength
                        || (currentLength == MIN_MATCH_LENGTH && currentOffset > FARTHEST_WORTHWHILE_SHORTEST_MATCH)) {
                    chooseLiteral(input[next++] & 0xFF, true, sequences, sequence);
                    continue;
                }
                next++;
                while (true) {
                    if (currentLength >= niceLength) {
                        sequence = chooseMatch(currentLength, currentOffset, true, sequences, sequence);
                        finder.skipBytes(input, next, length, currentLength - 1);
                        next += currentLength - 1;
                        break;
                    }
                    adjustMaxAndNiceLength(length - next);
                    int nextLength = finder.longestMatch(
                            input, next++, currentLength - 1, maxLength, niceLength, maxSearchDepth >> 1);
                    int nextOffset = finder.offset();
                    if (betterBy(nextLength, nextOffset, currentLength, currentOffset, GAIN_TO_WAIT_ONE_BYTE)) {
                        chooseLiteral(input[next - 2] & 0xFF, true, sequences, sequence);
                        currentLength = nextLength;
                        currentOffset = nextOffset;
                        continue;
                    }
                    if (looksTwoBytesAhead) {
                        adjustMaxAndNiceLength(length - next);
                        nextLength = finder.longestMatch(
                                input, next++, currentLength - 1, maxLength, niceLength, maxSearchDepth >> 2);
                        nextOffset = finder.offset();
                        if (betterBy(nextLength, nextOffset, currentLength, currentOffset, GAIN_TO_WAIT_TWO_BYTES)) {
                            chooseLiteral(input[next - 3] & 0xFF, true, sequences, sequence);
                            chooseLiteral(input[next - 2] & 0xFF, true, sequences, sequence);
                            currentLength = nextLength;
                            currentOffset = nextOffset;
                            continue;
                        }
                        sequence = chooseMatch(currentLength, currentOffset, true, sequences, sequence);
                        if (currentLength > 3) {
                            finder.skipBytes(input, next, length, currentLength - 3);
                            next += currentLength - 3;
                        }
                    } else {
                        sequence = chooseMatch(currentLength, currentOffset, true, sequences, sequence);
                        finder.skipBytes(input, next, length, currentLength - 2);
                        next += currentLength - 2;
                    }
                    break;
                }
            } while (next < blockEnd && sequence < GreedyParser.SEQUENCE_STORE_LENGTH
                    && !splitStatistics.shouldEndBlock(blockBegin, next, length));
            encoder.finishBlock(out, input, blockBegin, next - blockBegin, sequences, next == length);
        } while (next != length);
    }

    private boolean betterBy(int nextLength, int nextOffset, int currentLength, int currentOffset, int gain) {
        return nextLength >= currentLength
                && 4 * (nextLength - currentLength) + (highestBitOf(currentOffset) - highestBitOf(nextOffset)) > gain;
    }

    private int highestBitOf(int value) {
        return 31 - Integer.numberOfLeadingZeros(value);
    }
}
