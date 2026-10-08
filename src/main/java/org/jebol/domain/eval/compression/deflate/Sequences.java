package org.jebol.domain.eval.compression.deflate;

final class Sequences implements BlockContent {

    static final int LENGTH_SHIFT = 23;

    static final int LITERAL_RUN_MASK = (1 << LENGTH_SHIFT) - 1;

    final int[] literalRunAndLength;

    final int[] offsets;

    final int[] offsetSlots;

    Sequences(int capacity) {
        literalRunAndLength = new int[capacity + 1];
        offsets = new int[capacity + 1];
        offsetSlots = new int[capacity + 1];
    }

    Sequences holdingOnlyLiterals(int count) {
        literalRunAndLength[0] = count;
        return this;
    }

    @Override
    public void writeWith(BlockEncoder encoder, HuffmanCodes codes, BitWriter out, byte[] input, int begin) {
        int at = begin;
        for (int sequence = 0; ; sequence++) {
            int literalRun = literalRunAndLength[sequence] & LITERAL_RUN_MASK;
            int length = literalRunAndLength[sequence] >>> LENGTH_SHIFT;
            for (; literalRun > 0; literalRun--) {
                encoder.writeLiteral(out, codes, input[at++] & 0xFF);
            }
            if (length == 0) {
                return;
            }
            encoder.writeMatch(out, codes, length, offsets[sequence], offsetSlots[sequence]);
            at += length;
        }
    }
}
