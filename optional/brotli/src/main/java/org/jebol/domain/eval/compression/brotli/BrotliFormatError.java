package org.jebol.domain.eval.compression.brotli;

import java.io.Serial;

public final class BrotliFormatError extends IllegalArgumentException {

    static final int NEEDS_MORE_INPUT = 2;
    static final int EXUBERANT_NIBBLE = -1;
    static final int RESERVED = -2;
    static final int EXUBERANT_META_NIBBLE = -3;
    static final int SIMPLE_HUFFMAN_ALPHABET = -4;
    static final int SIMPLE_HUFFMAN_SAME = -5;
    static final int CL_SPACE = -6;
    static final int HUFFMAN_SPACE = -7;
    static final int CONTEXT_MAP_REPEAT = -8;
    static final int BLOCK_LENGTH_2 = -10;
    static final int TRANSFORM = -11;
    static final int DICTIONARY = -12;
    static final int WINDOW_BITS = -13;
    static final int PADDING_1 = -14;
    static final int PADDING_2 = -15;
    static final int DISTANCE = -16;
    static final int BLOCK_SWITCH = -17;
    static final int UNREACHABLE = -31;

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    BrotliFormatError(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
