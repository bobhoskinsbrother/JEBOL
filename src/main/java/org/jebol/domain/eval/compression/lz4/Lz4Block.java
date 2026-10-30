package org.jebol.domain.eval.compression.lz4;

import java.util.Arrays;

final class Lz4Block {

    static final int MINIMUM_MATCH = 4;
    static final int LAST_LITERALS = 5;
    static final int MATCH_FIND_LIMIT = 12;
    static final int SHORTEST_COMPRESSED_INPUT = MATCH_FIND_LIMIT + 1;
    static final int FARTHEST_OFFSET = 65535;
    static final int PREFIX_INDEX = 64 * 1024;

    private static final int RUN_MASK = 15;
    private static final int MATCH_LENGTH_MASK = 15;

    final byte[] input;
    final int inputEnd;
    final int matchFindLimit;
    final int matchLimit;
    int at;
    int anchor;
    private final byte[] output;
    private int written;

    Lz4Block(byte[] input) {
        this.input = input;
        inputEnd = input.length;
        matchFindLimit = inputEnd - MATCH_FIND_LIMIT;
        matchLimit = inputEnd - LAST_LITERALS;
        output = new byte[boundFor(input.length)];
    }

    static int boundFor(int length) {
        return length + length / 255 + 16;
    }

    void writeSequence(int matchLength, int offset) {
        int token = written++;
        int literals = at - anchor;
        if (literals >= RUN_MASK) {
            output[token] = (byte) (RUN_MASK << 4);
            writeLengthRemainder(literals - RUN_MASK);
        } else {
            output[token] = (byte) (literals << 4);
        }
        System.arraycopy(input, anchor, output, written, literals);
        written += literals;
        output[written++] = (byte) offset;
        output[written++] = (byte) (offset >>> 8);
        int lengthCode = matchLength - MINIMUM_MATCH;
        if (lengthCode >= MATCH_LENGTH_MASK) {
            output[token] += (byte) MATCH_LENGTH_MASK;
            writeLengthRemainder(lengthCode - MATCH_LENGTH_MASK);
        } else {
            output[token] += (byte) lengthCode;
        }
        at += matchLength;
        anchor = at;
    }

    private void writeLengthRemainder(int remainder) {
        int left = remainder;
        for (; left >= 255; left -= 255) {
            output[written++] = (byte) 255;
        }
        output[written++] = (byte) left;
    }

    byte[] finishWithLastLiterals() {
        int literals = inputEnd - anchor;
        if (literals >= RUN_MASK) {
            output[written++] = (byte) (RUN_MASK << 4);
            writeLengthRemainder(literals - RUN_MASK);
        } else {
            output[written++] = (byte) (literals << 4);
        }
        System.arraycopy(input, anchor, output, written, literals);
        written += literals;
        return Arrays.copyOf(output, written);
    }

    int indexOf(int position) {
        return position + PREFIX_INDEX;
    }
}
