package org.jebol.domain.eval.brotli;

record BrotliDistances(int directCodes, int postfixBits) {

    private static final int MOST_BITS_A_DISTANCE_MAY_NEED = 24;

    static final int FURTHEST = 0x3FFFFFC;

    static final BrotliDistances PLAINEST = new BrotliDistances(0, 0);

    static int alphabetSizeFor(int directCodes, int postfixBits) {
        return BrotliCommand.DISTANCE_SHORT_CODES + directCodes
                + (MOST_BITS_A_DISTANCE_MAY_NEED << (postfixBits + 1));
    }

    int alphabetSize() {
        return alphabetSizeFor(directCodes, postfixBits);
    }
}
