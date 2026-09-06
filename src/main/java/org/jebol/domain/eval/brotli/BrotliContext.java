package org.jebol.domain.eval.brotli;

/**
 * Which of sixty-four contexts a literal is written in.
 *
 * <p>Brotli conditions a literal on the two bytes before it, and a meta-block
 * says by which of four rules. Two of them are plain -- the low six bits or the
 * high six bits of the previous byte, ignoring the one before that -- and two
 * are not: one reads the pair as UTF-8 and asks what kind of character each
 * was, and one buckets each byte by magnitude for data that is really numbers.
 *
 * <p>{@code context.c}. Only the UTF-8 tables are written down; the other
 * three follow from a line of arithmetic each, and a test checks that all four
 * still agree with the C's own 2,048 bytes.
 */
final class BrotliContext {

    private BrotliContext() {
    }

    static final int LOW_SIX_BITS = 0;
    static final int HIGH_SIX_BITS = 1;
    static final int UTF8 = 2;
    static final int SIGNED = 3;

    /** What the previous byte contributes, when the pair is read as UTF-8. */
    private static final int[] UTF8_FROM_THE_LAST_BYTE = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 4, 0, 0, 4, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 8, 12, 16, 12, 12, 20, 12, 16,
            24, 28, 12, 12, 32, 12, 36, 12, 44, 44, 44, 44, 44, 44, 44, 44, 44, 44, 32, 32,
            24, 40, 28, 12, 12, 48, 52, 52, 52, 48, 52, 52, 52, 48, 52, 52, 52, 52, 52, 48,
            52, 52, 52, 52, 52, 48, 52, 52, 52, 52, 52, 24, 12, 28, 12, 12, 12, 56, 60, 60,
            60, 56, 60, 60, 60, 56, 60, 60, 60, 60, 60, 56, 60, 60, 60, 60, 60, 56, 60, 60,
            60, 60, 60, 24, 12, 28, 12, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1,
            0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1,
            0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1,
            0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 2, 3, 2, 3, 2, 3, 2, 3,
            2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3,
            2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3,
            2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3, 2, 3,
    };

    /** And what the byte before that contributes. */
    private static final int[] UTF8_FROM_THE_BYTE_BEFORE = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1,
            1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
            3, 3, 3, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
    };

    /**
     * How far from zero a byte is, in eight steps.
     *
     * <p>The signed mode is for arrays of numbers, where what matters about a
     * byte is roughly how big it is rather than which character it spells.
     */
    private static int magnitudeOf(int octet) {
        if (octet == 0) {
            return 0;
        }
        if (octet < 16) {
            return 1;
        }
        if (octet < 64) {
            return 2;
        }
        if (octet < 128) {
            return 3;
        }
        if (octet < 192) {
            return 4;
        }
        if (octet < 240) {
            return 5;
        }
        return octet < 255 ? 6 : 7;
    }

    static int of(int mode, int previous, int beforeThat) {
        return switch (mode) {
            case LOW_SIX_BITS -> previous & 63;
            case HIGH_SIX_BITS -> previous >> 2;
            case UTF8 -> UTF8_FROM_THE_LAST_BYTE[previous]
                    | UTF8_FROM_THE_BYTE_BEFORE[beforeThat];
            default -> (magnitudeOf(previous) << 3) | magnitudeOf(beforeThat);
        };
    }
}
