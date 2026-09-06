package org.jebol.domain.eval;

/**
 * How a meta-block spells its distances.
 *
 * <p>A distance is written as a code plus some extra bits. Two knobs change the
 * split: how many of the smallest distances get a code of their own with no
 * extra bits at all, and how many low bits of the rest are moved out of the
 * extra bits and into the code. Both are zero to begin with; a meta-block may
 * pick better ones once it has seen every distance it needs to write, and the
 * commands are then re-coded.
 *
 * <p>{@code BrotliInitDistanceParams} in {@code params.c}, for the window sizes
 * this port meets. The window is never widened past the format's ordinary
 * twenty-four bits here, because nothing in Rebol asks for it.
 */
record BrotliDistances(int directCodes, int postfixBits) {

    private static final int MOST_BITS_A_DISTANCE_MAY_NEED = 24;

    /** The largest distance the ordinary window can name. */
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
