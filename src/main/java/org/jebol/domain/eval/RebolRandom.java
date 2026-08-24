package org.jebol.domain.eval;

/**
 * Rebol's own random numbers, so that a seeded shuffle comes out the same.
 *
 * <p>Knuth's lagged Fibonacci generator from {@code src/core/f-random.c},
 * which is his published {@code ran_array} with one change Rebol carries: the
 * modulus is 2^62 rather than 2^30, so the numbers are 62 bits wide.
 *
 * <p>This is here rather than {@code java.util.Random} for one reason, and it
 * is the only reason that would justify it. {@code random/seed 1} followed by
 * {@code random "stesti"} is asserted to give {@code "sistte"} in Rebol's own
 * tests, and that is a statement about this exact sequence of numbers. No
 * other generator produces it, however well distributed it is, so a port that
 * uses a different one cannot agree with Rebol about anything a script seeds.
 *
 * <p>One per interpreter rather than one per process. The C keeps its state in
 * file statics, which is the same thing for a program that runs one
 * interpreter; here a host runs many at once and a seed set in one must not
 * move another.
 */
final class RebolRandom {

    private static final int LONG_LAG = 100;
    private static final int SHORT_LAG = 37;
    private static final long MODULUS = 1L << 62;
    private static final int QUALITY = 1009;
    private static final int STREAM_SEPARATION = 70;

    /** What the C seeds with when a script asks for a number and never seeded. */
    private static final long WHEN_NOBODY_CHOSE = 314159L;

    private final long[] state = new long[LONG_LAG];
    private final long[] drawn = new long[QUALITY];
    private int cursor = -1;
    private boolean everSeeded;

    private static long modDiff(long left, long right) {
        return (left - right) & (MODULUS - 1);
    }

    /**
     * The next number, between zero and the modulus.
     *
     * <p>{@code ran_arr_next}: take one from the buffer while there is one,
     * and refill when the marker at the end is reached. The C writes the
     * marker as a negative number in the buffer, which is why every real value
     * has to be non-negative and why the modulus is what it is.
     */
    long next() {
        if (cursor >= 1 && cursor < LONG_LAG) {
            return drawn[cursor++];
        }
        return refill();
    }

    private long refill() {
        if (!everSeeded) {
            seed(WHEN_NOBODY_CHOSE);
        }
        fill(drawn, QUALITY);
        cursor = 1;
        return drawn[0];
    }

    /**
     * Starts the sequence again from a chosen point.
     *
     * <p>Knuth's {@code ran_start}, which squares and multiplies its way to a
     * state seventy streams away from the seed so that two nearby seeds do not
     * give two similar sequences. The ten warm-up rounds at the end are his
     * too, and leaving them out changes every number that follows.
     */
    void seed(long chosen) {
        long[] preparing = new long[LONG_LAG + LONG_LAG - 1];
        long spread = (chosen + 2) & (MODULUS - 2);
        for (int at = 0; at < LONG_LAG; at++) {
            preparing[at] = spread;
            spread <<= 1;
            if (spread >= MODULUS) {
                spread -= MODULUS - 2;
            }
        }
        preparing[1]++;
        long remaining = chosen & (MODULUS - 1);
        for (int rounds = STREAM_SEPARATION - 1; rounds > 0;) {
            for (int at = LONG_LAG - 1; at > 0; at--) {
                preparing[at + at] = preparing[at];
                preparing[at + at - 1] = 0;
            }
            for (int at = LONG_LAG + LONG_LAG - 2; at >= LONG_LAG; at--) {
                preparing[at - (LONG_LAG - SHORT_LAG)] =
                        modDiff(preparing[at - (LONG_LAG - SHORT_LAG)], preparing[at]);
                preparing[at - LONG_LAG] =
                        modDiff(preparing[at - LONG_LAG], preparing[at]);
            }
            if ((remaining & 1) == 1) {
                for (int at = LONG_LAG; at > 0; at--) {
                    preparing[at] = preparing[at - 1];
                }
                preparing[0] = preparing[LONG_LAG];
                preparing[SHORT_LAG] =
                        modDiff(preparing[SHORT_LAG], preparing[LONG_LAG]);
            }
            if (remaining != 0) {
                remaining >>= 1;
            } else {
                rounds--;
            }
        }
        for (int at = 0; at < SHORT_LAG; at++) {
            state[at + LONG_LAG - SHORT_LAG] = preparing[at];
        }
        for (int at = SHORT_LAG; at < LONG_LAG; at++) {
            state[at - SHORT_LAG] = preparing[at];
        }
        for (int round = 0; round < 10; round++) {
            fill(preparing, LONG_LAG + LONG_LAG - 1);
        }
        everSeeded = true;
        cursor = -1;
    }

    /** {@code ran_array}: fill a buffer and carry the state forward. */
    private void fill(long[] into, int howMany) {
        int at = 0;
        for (; at < LONG_LAG; at++) {
            into[at] = state[at];
        }
        for (; at < howMany; at++) {
            into[at] = modDiff(into[at - LONG_LAG], into[at - SHORT_LAG]);
        }
        int back = 0;
        for (; back < SHORT_LAG; back++, at++) {
            state[back] = modDiff(into[at - LONG_LAG], into[at - SHORT_LAG]);
        }
        for (; back < LONG_LAG; back++, at++) {
            state[back] = modDiff(into[at - LONG_LAG], state[back - SHORT_LAG]);
        }
    }

    /**
     * A number below a limit, the way the shuffles and the pickers ask.
     *
     * <p>{@code (REBCNT)Random_Int(secure) % n}, and the cast is not
     * decoration: REBCNT is thirty-two bits, so the sixty-two-bit number is
     * cut down before the remainder is taken and the answer is not the one the
     * full number would give. Taking the remainder of the whole thing produced
     * a perfectly good shuffle that disagreed with Rebol's on every seed.
     */
    int below(int limit) {
        return limit <= 0 ? 0 : (int) (Integer.toUnsignedLong((int) next()) % limit);
    }

    /**
     * The same, without the narrowing.
     *
     * <p>{@code REBTYPE(Tuple)} writes {@code Random_Int(...) % (1 + *vp)}
     * with no cast in front of it, so a tuple's octets come from a different
     * arithmetic than everything else does.
     */
    int belowWithoutNarrowing(int limit) {
        return limit <= 0 ? 0 : (int) Long.remainderUnsigned(next(), limit);
    }
}
