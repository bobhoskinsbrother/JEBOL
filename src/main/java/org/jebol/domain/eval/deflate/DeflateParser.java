package org.jebol.domain.eval.deflate;

import static org.jebol.domain.eval.deflate.DeflateTables.FIRST_LENGTH_SYMBOL;
import static org.jebol.domain.eval.deflate.DeflateTables.LENGTH_SLOT;
import static org.jebol.domain.eval.deflate.DeflateTables.LITERAL_COUNT;
import static org.jebol.domain.eval.deflate.DeflateTables.MAX_MATCH_LENGTH;
import static org.jebol.domain.eval.deflate.DeflateTables.MIN_BLOCK_LENGTH;
import static org.jebol.domain.eval.deflate.DeflateTables.MIN_MATCH_LENGTH;

abstract class DeflateParser {

    private static final int[] MIN_LENGTH_BY_LITERALS_USED = {
        9, 9, 9, 9, 9, 9, 8, 8, 7, 7, 6, 6, 6, 6, 6, 6,
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 4, 4, 4,
        4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
        4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
    };

    private static final int SHALLOW_SEARCH = 16;

    private static final int VERY_SHALLOW_SEARCH = 5;

    private static final int FAIRLY_SHALLOW_SEARCH = 10;

    private static final int SHORT_ENOUGH_FOR_ANY_MATCH = 512;

    private static final int PREFIX_SCANNED_FOR_LITERALS = 4096;

    private static final int RARE_LITERAL_SHIFT = 10;

    protected final BlockEncoder encoder;

    protected final BlockSplitStatistics splitStatistics = new BlockSplitStatistics();

    protected final int maxSearchDepth;

    protected int maxLength = MAX_MATCH_LENGTH;

    protected int niceLength;

    DeflateParser(BlockEncoder encoder, int maxSearchDepth, int niceMatchLength) {
        this.encoder = encoder;
        this.maxSearchDepth = maxSearchDepth;
        this.niceLength = Math.min(niceMatchLength, MAX_MATCH_LENGTH);
    }

    abstract void compress(byte[] input, int length, BitWriter out);

    void beginSequences(Sequences sequences) {
        encoder.resetFrequencies();
        sequences.literalRunAndLength[0] = 0;
    }

    void chooseLiteral(int literal, boolean gatheringStatistics, Sequences sequences, int sequence) {
        encoder.litlenFrequencies[literal]++;
        if (gatheringStatistics) {
            splitStatistics.observeLiteral(literal);
        }
        sequences.literalRunAndLength[sequence]++;
    }

    int chooseMatch(int length, int offset, boolean gatheringStatistics, Sequences sequences, int sequence) {
        int offsetSlot = encoder.offsetSlotOf(offset);
        encoder.litlenFrequencies[FIRST_LENGTH_SYMBOL + LENGTH_SLOT[length]]++;
        encoder.offsetFrequencies[offsetSlot]++;
        if (gatheringStatistics) {
            splitStatistics.observeMatch(length);
        }
        sequences.literalRunAndLength[sequence] |= length << Sequences.LENGTH_SHIFT;
        sequences.offsets[sequence] = offset;
        sequences.offsetSlots[sequence] = offsetSlot;
        sequences.literalRunAndLength[sequence + 1] = 0;
        return sequence + 1;
    }

    void adjustMaxAndNiceLength(int remaining) {
        if (remaining < MAX_MATCH_LENGTH) {
            maxLength = remaining;
            niceLength = Math.min(niceLength, maxLength);
        }
    }

    int maxBlockEnd(int blockBegin, int end, int softMaxLength) {
        if (end - blockBegin < softMaxLength + MIN_BLOCK_LENGTH) {
            return end;
        }
        return blockBegin + softMaxLength;
    }

    int minMatchLengthFor(int literalsUsed) {
        if (literalsUsed >= MIN_LENGTH_BY_LITERALS_USED.length) {
            return MIN_MATCH_LENGTH;
        }
        int minLength = MIN_LENGTH_BY_LITERALS_USED[literalsUsed];
        if (maxSearchDepth < SHALLOW_SEARCH) {
            if (maxSearchDepth < VERY_SHALLOW_SEARCH) {
                minLength = 4;
            } else if (maxSearchDepth < FAIRLY_SHALLOW_SEARCH) {
                minLength = Math.min(minLength, 5);
            } else {
                minLength = Math.min(minLength, 7);
            }
        }
        return minLength;
    }

    int minMatchLengthScanning(byte[] input, int from, int length) {
        if (length < SHORT_ENOUGH_FOR_ANY_MATCH) {
            return MIN_MATCH_LENGTH;
        }
        boolean[] used = new boolean[LITERAL_COUNT];
        int scanned = Math.min(length, PREFIX_SCANNED_FOR_LITERALS);
        for (int at = 0; at < scanned; at++) {
            used[input[from + at] & 0xFF] = true;
        }
        int literalsUsed = 0;
        for (boolean each : used) {
            literalsUsed += each ? 1 : 0;
        }
        return minMatchLengthFor(literalsUsed);
    }

    int minMatchLengthFromTheFrequencies() {
        int literalFrequency = 0;
        for (int literal = 0; literal < LITERAL_COUNT; literal++) {
            literalFrequency += encoder.litlenFrequencies[literal];
        }
        int cutoff = literalFrequency >>> RARE_LITERAL_SHIFT;
        int literalsUsed = 0;
        for (int literal = 0; literal < LITERAL_COUNT; literal++) {
            if (encoder.litlenFrequencies[literal] > cutoff) {
                literalsUsed++;
            }
        }
        return minMatchLengthFor(literalsUsed);
    }
}
