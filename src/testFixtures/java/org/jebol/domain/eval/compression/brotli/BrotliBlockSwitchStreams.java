package org.jebol.domain.eval.compression.brotli;

public final class BrotliBlockSwitchStreams {

    public static final int BLOCK_SIZE_CAP = 1 << 24;
    public static final int WORD_LENGTH = 4;

    private static final int SHIFT_FOR_FOUR_LETTER_WORDS = 10;
    private static final int TRIPLE_COPY_COMMAND = 130;
    private static final int LONGEST_BLOCK_LENGTH_SYMBOL = 25;
    private static final int LONGEST_BLOCK_LENGTH_OFFSET = 16625;

    private BrotliBlockSwitchStreams() {
    }

    public static int findFirstTransformEmptyingWord() {
        byte[] out = new byte[600];
        for (int transform = 1; transform < BrotliDictionary.TRANSFORM_COUNT; transform++) {
            if (BrotliDictionary.writeTransformedWord(out, 0,
                    BrotliDictionary.offsetFor(WORD_LENGTH), WORD_LENGTH, transform) == 0) {
                return transform;
            }
        }
        throw new IllegalStateException("no transform empties a four-letter word");
    }

    private record DistanceCode(int symbol, int extraBits, int extra) {

        static DistanceCode forDistance(int distance) {
            for (int symbol = 16; symbol < 64; symbol++) {
                int bits = 1 + ((symbol - 16) >> 1);
                int offset = ((2 + ((symbol - 16) & 1)) << bits) - 4;
                if (distance - 1 >= offset && distance - 1 - offset < (1 << bits)) {
                    return new DistanceCode(symbol, bits, distance - 1 - offset);
                }
            }
            throw new IllegalArgumentException("no plain distance code reaches " + distance);
        }
    }

    private static void writeCommandBlockTypes(BrotliBits bits, int commandTypes) {
        if (commandTypes == 1) {
            bits.write(1, 0);
            return;
        }
        bits.write(1, 1);
        bits.write(3, 0);
        bits.write(2, 1);
        bits.write(2, 0);
        bits.write(2, 0);
        bits.write(2, 1);
        bits.write(2, 0);
        bits.write(5, LONGEST_BLOCK_LENGTH_SYMBOL);
        bits.write(24, BLOCK_SIZE_CAP + 2 - LONGEST_BLOCK_LENGTH_OFFSET);
    }

    public static byte[] buildEmptyWordsThenWord(int emptyWords, int commandTypes) {
        int transform = findFirstTransformEmptyingWord();
        DistanceCode empty = DistanceCode.forDistance(1 + (transform << SHIFT_FOR_FOUR_LETTER_WORDS));
        DistanceCode word = DistanceCode.forDistance(1);
        BrotliBits bits = new BrotliBits(emptyWords / 8 * (2 + empty.extraBits()) + 64);
        bits.write(1, 0);
        bits.write(1, 1);
        bits.write(1, 0);
        bits.write(2, 0);
        bits.write(16, WORD_LENGTH - 1);
        bits.write(1, 0);
        writeCommandBlockTypes(bits, commandTypes);
        bits.write(1, 0);
        bits.write(6, 0);
        bits.write(2, 0);
        bits.write(2, 0);
        bits.write(2, 1);
        bits.write(2, 0);
        bits.write(8, 'a');
        for (int each = 0; each < commandTypes; each++) {
            bits.write(2, 1);
            bits.write(2, 0);
            bits.write(10, TRIPLE_COPY_COMMAND);
        }
        bits.write(2, 1);
        bits.write(2, 1);
        bits.write(6, word.symbol());
        bits.write(6, empty.symbol());
        for (int each = 0; each < emptyWords; each++) {
            bits.write(1, 1);
            bits.write(empty.extraBits(), empty.extra());
        }
        bits.write(1, 0);
        bits.write(word.extraBits(), word.extra());
        bits.jumpToByteBoundary();
        return bits.written();
    }
}
