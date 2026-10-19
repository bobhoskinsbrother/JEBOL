package org.jebol.domain.eval.compression.deflate;

final class HuffmanCodes {

    static final int OFFSET_LENGTHS_FROM = DeflateTables.LITLEN_SYMBOL_COUNT;

    final int[] lengths = new int[DeflateTables.LITLEN_SYMBOL_COUNT + DeflateTables.OFFSET_SYMBOL_COUNT];

    final int[] litlenCodewords = new int[DeflateTables.LITLEN_SYMBOL_COUNT];

    final int[] offsetCodewords = new int[DeflateTables.OFFSET_SYMBOL_COUNT];

    int readTheLitlenLength(int symbol) {
        return lengths[symbol];
    }

    int readTheOffsetLength(int slot) {
        return lengths[OFFSET_LENGTHS_FROM + slot];
    }
}
