package org.jebol.domain.eval.compression.zstd;

final class ZstdSequenceStore {

    static final int LONG_LENGTH_NONE = 0;
    static final int LONG_LENGTH_LITERAL = 1;
    static final int LONG_LENGTH_MATCH = 2;
    static final int MIN_MATCH = 3;
    static final int REPEAT_COUNT = 3;

    private static final int[] LITERAL_LENGTH_CODES = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 16, 17, 17, 18, 18, 19, 19, 20, 20, 20, 20, 21, 21, 21, 21,
            22, 22, 22, 22, 22, 22, 22, 22, 23, 23, 23, 23, 23, 23, 23, 23,
            24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24};
    private static final int[] MATCH_LENGTH_CODES = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31,
            32, 32, 33, 33, 34, 34, 35, 35, 36, 36, 36, 36, 37, 37, 37, 37,
            38, 38, 38, 38, 38, 38, 38, 38, 39, 39, 39, 39, 39, 39, 39, 39,
            40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40,
            41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41,
            42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42,
            42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42};

    final int[] offBases;
    final int[] literalLengths;
    final int[] matchLengthBases;
    final byte[] literals;
    final byte[] literalLengthCodes;
    final byte[] matchLengthCodes;
    final byte[] offsetCodes;
    int sequencesStart;
    int sequencesEnd;
    int literalsStart;
    int literalsEnd;
    int longLengthType;
    int longLengthPosition;

    ZstdSequenceStore(int maxSequences, int maxLiterals) {
        offBases = new int[maxSequences];
        literalLengths = new int[maxSequences];
        matchLengthBases = new int[maxSequences];
        literals = new byte[maxLiterals + ZstdDecompressionContext.WILDCOPY_OVERLENGTH];
        literalLengthCodes = new byte[maxSequences];
        matchLengthCodes = new byte[maxSequences];
        offsetCodes = new byte[maxSequences];
    }

    private ZstdSequenceStore(ZstdSequenceStore shared) {
        offBases = shared.offBases;
        literalLengths = shared.literalLengths;
        matchLengthBases = shared.matchLengthBases;
        literals = shared.literals;
        literalLengthCodes = shared.literalLengthCodes;
        matchLengthCodes = shared.matchLengthCodes;
        offsetCodes = shared.offsetCodes;
    }

    ZstdSequenceStore sharingArrays() {
        return new ZstdSequenceStore(this);
    }

    void copyFrom(ZstdSequenceStore other) {
        sequencesStart = other.sequencesStart;
        sequencesEnd = other.sequencesEnd;
        literalsStart = other.literalsStart;
        literalsEnd = other.literalsEnd;
        longLengthType = other.longLengthType;
        longLengthPosition = other.longLengthPosition;
    }

    void reset() {
        literalsEnd = literalsStart;
        sequencesEnd = sequencesStart;
        longLengthType = LONG_LENGTH_NONE;
    }

    int count() {
        return sequencesEnd - sequencesStart;
    }

    int literalSize() {
        return literalsEnd - literalsStart;
    }

    void storeSequenceOnly(int literalLength, int offBase, int matchLength) {
        if (literalLength > 0xFFFF) {
            longLengthType = LONG_LENGTH_LITERAL;
            longLengthPosition = sequencesEnd - sequencesStart;
        }
        literalLengths[sequencesEnd] = literalLength & 0xFFFF;
        offBases[sequencesEnd] = offBase;
        int matchLengthBase = matchLength - MIN_MATCH;
        if (matchLengthBase > 0xFFFF) {
            longLengthType = LONG_LENGTH_MATCH;
            longLengthPosition = sequencesEnd - sequencesStart;
        }
        matchLengthBases[sequencesEnd] = matchLengthBase & 0xFFFF;
        sequencesEnd++;
    }

    void storeSequence(int literalLength, byte[] source, int from, int offBase, int matchLength) {
        System.arraycopy(source, from, literals, literalsEnd, literalLength);
        literalsEnd += literalLength;
        storeSequenceOnly(literalLength, offBase, matchLength);
    }

    void storeLastLiterals(byte[] source, int from, int length) {
        System.arraycopy(source, from, literals, literalsEnd, length);
        literalsEnd += length;
    }

    static int literalLengthCode(int literalLength) {
        return literalLength > 63 ? ZstdOctets.highBit(literalLength) + 19 : LITERAL_LENGTH_CODES[literalLength];
    }

    static int matchLengthCode(int matchLengthBase) {
        return matchLengthBase > 127 ? ZstdOctets.highBit(matchLengthBase) + 36 : MATCH_LENGTH_CODES[matchLengthBase];
    }

    void toCodes() {
        for (int at = sequencesStart; at < sequencesEnd; at++) {
            literalLengthCodes[at] = (byte) literalLengthCode(literalLengths[at]);
            offsetCodes[at] = (byte) ZstdOctets.highBit(offBases[at]);
            matchLengthCodes[at] = (byte) matchLengthCode(matchLengthBases[at]);
        }
        if (longLengthType == LONG_LENGTH_LITERAL) {
            literalLengthCodes[sequencesStart + longLengthPosition] = (byte) ZstdEntropyTables.MAX_LITERAL_LENGTH;
        }
        if (longLengthType == LONG_LENGTH_MATCH) {
            matchLengthCodes[sequencesStart + longLengthPosition] = (byte) ZstdEntropyTables.MAX_MATCH_LENGTH;
        }
    }

    long countLiteralBytes() {
        long total = 0;
        for (int at = sequencesStart; at < sequencesEnd; at++) {
            total += literalLengths[at];
            if (at - sequencesStart == longLengthPosition && longLengthType == LONG_LENGTH_LITERAL) {
                total += 0x10000;
            }
        }
        return total;
    }

    long countMatchBytes() {
        long total = 0;
        for (int at = sequencesStart; at < sequencesEnd; at++) {
            total += matchLengthBases[at] + MIN_MATCH;
            if (at - sequencesStart == longLengthPosition && longLengthType == LONG_LENGTH_MATCH) {
                total += 0x10000;
            }
        }
        return total;
    }

    void deriveChunk(ZstdSequenceStore original, int startIndex, int endIndex) {
        copyFrom(original);
        if (startIndex > 0) {
            sequencesEnd = original.sequencesStart + startIndex;
            literalsStart += (int) countLiteralBytes();
        }
        if (original.longLengthType != LONG_LENGTH_NONE) {
            if (Integer.compareUnsigned(original.longLengthPosition, startIndex) < 0
                    || Integer.compareUnsigned(original.longLengthPosition, endIndex) > 0) {
                longLengthType = LONG_LENGTH_NONE;
            } else {
                longLengthPosition -= startIndex;
            }
        }
        sequencesStart = original.sequencesStart + startIndex;
        sequencesEnd = original.sequencesStart + endIndex;
        if (endIndex != original.count()) {
            literalsEnd = literalsStart + (int) countLiteralBytes();
        }
    }

    static void updateRepeats(int[] repeats, int offBase, int isLiteralLengthZero) {
        if (offBase > REPEAT_COUNT) {
            repeats[2] = repeats[1];
            repeats[1] = repeats[0];
            repeats[0] = offBase - REPEAT_COUNT;
        } else {
            int repeatCode = offBase - 1 + isLiteralLengthZero;
            if (repeatCode > 0) {
                int currentOffset = repeatCode == REPEAT_COUNT ? repeats[0] - 1 : repeats[repeatCode];
                repeats[2] = repeatCode >= 2 ? repeats[1] : repeats[2];
                repeats[1] = repeats[0];
                repeats[0] = currentOffset;
            }
        }
    }
}
