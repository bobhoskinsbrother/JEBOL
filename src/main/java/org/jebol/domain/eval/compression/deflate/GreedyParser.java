package org.jebol.domain.eval.compression.deflate;

import static org.jebol.domain.eval.compression.deflate.DeflateTables.MIN_MATCH_LENGTH;
import static org.jebol.domain.eval.compression.deflate.DeflateTables.SOFT_MAX_BLOCK_LENGTH;

final class GreedyParser extends DeflateParser {

    static final int SEQUENCE_STORE_LENGTH = 50000;

    private static final int FARTHEST_WORTHWHILE_SHORTEST_MATCH = 4096;

    private final HashChainMatchFinder finder = new HashChainMatchFinder();

    private final Sequences sequences = new Sequences(SEQUENCE_STORE_LENGTH);

    GreedyParser(BlockEncoder encoder, int maxSearchDepth, int niceMatchLength) {
        super(encoder, maxSearchDepth, niceMatchLength);
    }

    @Override
    void compress(byte[] input, int length, BitWriter out) {
        int next = 0;
        do {
            int blockBegin = next;
            int blockEnd = maxBlockEnd(next, length, SOFT_MAX_BLOCK_LENGTH);
            int sequence = 0;
            splitStatistics.reset();
            beginSequences(sequences);
            int minLength = minMatchLengthScanning(input, next, blockEnd - next);
            do {
                adjustMaxAndNiceLength(length - next);
                int matched = finder.longestMatch(input, next, minLength - 1, maxLength, niceLength, maxSearchDepth);
                int offset = finder.offset();
                if (matched >= minLength
                        && (matched > MIN_MATCH_LENGTH || offset <= FARTHEST_WORTHWHILE_SHORTEST_MATCH)) {
                    sequence = chooseMatch(matched, offset, true, sequences, sequence);
                    finder.skipBytes(input, next + 1, length, matched - 1);
                    next += matched;
                } else {
                    chooseLiteral(input[next++] & 0xFF, true, sequences, sequence);
                }
            } while (next < blockEnd && sequence < SEQUENCE_STORE_LENGTH
                    && !splitStatistics.shouldEndBlock(blockBegin, next, length));
            encoder.finishBlock(out, input, blockBegin, next - blockBegin, sequences, next == length);
        } while (next != length);
    }
}
