package org.jebol.domain.eval.compression.zstd;

final class ZstdEntropyTables {

    static final int FSE_REPEAT_NONE = 0;
    static final int FSE_REPEAT_CHECK = 1;
    static final int FSE_REPEAT_VALID = 2;

    static final int MAX_LITERAL_LENGTH = 35;
    static final int MAX_MATCH_LENGTH = 52;
    static final int MAX_OFFSET = 31;
    static final int LITERAL_LENGTH_LOG = 9;
    static final int MATCH_LENGTH_LOG = 9;
    static final int OFFSET_LOG = 8;

    final ZstdHuffmanTable huffman = new ZstdHuffmanTable();
    int huffmanRepeat;
    final ZstdFseEncoding offsets = new ZstdFseEncoding(OFFSET_LOG, MAX_OFFSET);
    final ZstdFseEncoding matchLengths = new ZstdFseEncoding(MATCH_LENGTH_LOG, MAX_MATCH_LENGTH);
    final ZstdFseEncoding literalLengths = new ZstdFseEncoding(LITERAL_LENGTH_LOG, MAX_LITERAL_LENGTH);
    int offsetRepeat;
    int matchLengthRepeat;
    int literalLengthRepeat;
    final int[] repeats = new int[3];

    void copyHuffmanFrom(ZstdEntropyTables other) {
        huffman.copyFrom(other.huffman);
        huffmanRepeat = other.huffmanRepeat;
    }

    void copyFseFrom(ZstdEntropyTables other) {
        offsets.copyFrom(other.offsets);
        matchLengths.copyFrom(other.matchLengths);
        literalLengths.copyFrom(other.literalLengths);
        offsetRepeat = other.offsetRepeat;
        matchLengthRepeat = other.matchLengthRepeat;
        literalLengthRepeat = other.literalLengthRepeat;
    }

    void reset() {
        repeats[0] = 1;
        repeats[1] = 4;
        repeats[2] = 8;
        huffmanRepeat = ZstdHuffmanTable.REPEAT_NONE;
        offsetRepeat = FSE_REPEAT_NONE;
        matchLengthRepeat = FSE_REPEAT_NONE;
        literalLengthRepeat = FSE_REPEAT_NONE;
    }
}
