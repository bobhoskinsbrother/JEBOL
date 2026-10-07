package org.jebol.domain.eval.deflate;

import static org.jebol.domain.eval.deflate.DeflateTables.MAX_MATCH_LENGTH;

final class FastestParser extends DeflateParser {

    private static final int SOFT_MAX_BLOCK_LENGTH = 65535;

    private static final int SEQUENCE_STORE_LENGTH = 8192;

    private final HashTableMatchFinder finder = new HashTableMatchFinder();

    private final Sequences sequences = new Sequences(SEQUENCE_STORE_LENGTH);

    FastestParser(BlockEncoder encoder, int niceMatchLength) {
        super(encoder, 0, niceMatchLength);
    }

    @Override
    void compress(byte[] input, int length, BitWriter out) {
        int next = 0;
        do {
            int blockBegin = next;
            int blockEnd = maxBlockEnd(next, length, SOFT_MAX_BLOCK_LENGTH);
            int sequence = 0;
            beginSequences(sequences);
            do {
                int remaining = length - next;
                if (remaining < MAX_MATCH_LENGTH) {
                    maxLength = remaining;
                    if (maxLength < HashTableMatchFinder.REQUIRED_BYTES) {
                        do {
                            chooseLiteral(input[next++] & 0xFF, false, sequences, sequence);
                        } while (--maxLength != 0);
                        break;
                    }
                    niceLength = Math.min(niceLength, maxLength);
                }
                int matched = finder.longestMatch(input, next, maxLength, niceLength);
                if (matched != 0) {
                    sequence = chooseMatch(matched, finder.offset(), false, sequences, sequence);
                    finder.skipBytes(input, next + 1, length, matched - 1);
                    next += matched;
                } else {
                    chooseLiteral(input[next++] & 0xFF, false, sequences, sequence);
                }
            } while (next < blockEnd && sequence < SEQUENCE_STORE_LENGTH);
            encoder.finishBlock(out, input, blockBegin, next - blockBegin, sequences, next == length);
        } while (next != length);
    }
}
