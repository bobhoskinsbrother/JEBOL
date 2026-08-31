package org.jebol.domain.eval;

/**
 * xxHash, the thirty-two and sixty-four bit forms, written out because the JVM
 * has not got them.
 *
 * <p>{@code system/catalog/checksums} lists them beside the cryptographic
 * digests, and they are not one: xxHash is built to be fast over a lot of
 * bytes and makes no claim to be hard to reverse. A caller reaches for it to
 * tell whether two blocks differ, not to keep a secret.
 *
 * <p>Both forms are the same shape. A run of accumulators, four of them, each
 * taking every fourth word of the input; then the accumulators folded into one;
 * then the length added and the tail eaten a word and a byte at a time; then a
 * final scramble of shifts and multiplies to spread the bits. Only the
 * constants, the word width and the rotations differ.
 *
 * <p>Little-endian throughout, and the answer is written big-endian, which is
 * what makes {@code checksum "" 'xxh32} the four bytes {@code 02CC5D05} rather
 * than their reverse.
 */
final class XxHash {

    private XxHash() {
    }

    private static final int P32_1 = 0x9E3779B1;
    private static final int P32_2 = 0x85EBCA77;
    private static final int P32_3 = 0xC2B2AE3D;
    private static final int P32_4 = 0x27D4EB2F;
    private static final int P32_5 = 0x165667B1;

    private static final long P64_1 = 0x9E3779B185EBCA87L;
    private static final long P64_2 = 0xC2B2AE3D27D4EB4FL;
    private static final long P64_3 = 0x165667B19E3779F9L;
    private static final long P64_4 = 0x85EBCA77C2B2AE63L;
    private static final long P64_5 = 0x27D4EB2F165667C5L;

    /** The four-byte hash, most significant byte first. */
    static byte[] of32(byte[] message) {
        return asBigEndian(hash32(message) & 0xFFFFFFFFL, 4);
    }

    /** The eight-byte hash, most significant byte first. */
    static byte[] of64(byte[] message) {
        return asBigEndian(hash64(message), 8);
    }

    private static int hash32(byte[] message) {
        int at = 0;
        int running;
        if (message.length >= 16) {
            int first = P32_1 + P32_2;
            int second = P32_2;
            int third = 0;
            int fourth = -P32_1;
            while (at + 16 <= message.length) {
                first = stirred32(first, wordAt(message, at));
                second = stirred32(second, wordAt(message, at + 4));
                third = stirred32(third, wordAt(message, at + 8));
                fourth = stirred32(fourth, wordAt(message, at + 12));
                at += 16;
            }
            running = Integer.rotateLeft(first, 1) + Integer.rotateLeft(second, 7)
                    + Integer.rotateLeft(third, 12) + Integer.rotateLeft(fourth, 18);
        } else {
            running = P32_5;
        }
        running += message.length;
        while (at + 4 <= message.length) {
            running = Integer.rotateLeft(running + wordAt(message, at) * P32_3, 17) * P32_4;
            at += 4;
        }
        while (at < message.length) {
            running = Integer.rotateLeft(
                    running + (message[at] & 0xFF) * P32_5, 11) * P32_1;
            at++;
        }
        return scrambled32(running);
    }

    private static int stirred32(int accumulator, int word) {
        return Integer.rotateLeft(accumulator + word * P32_2, 13) * P32_1;
    }

    /**
     * The last shuffle, which is what makes a one-bit change to the input
     * change roughly half the answer.
     *
     * <p>Without it the accumulators leave their high bits barely mixed, and
     * two inputs differing only near the end come out close together -- which
     * is exactly what a hash is for avoiding.
     */
    private static int scrambled32(int running) {
        int mixed = running ^ running >>> 15;
        mixed *= P32_2;
        mixed ^= mixed >>> 13;
        mixed *= P32_3;
        return mixed ^ mixed >>> 16;
    }

    private static long hash64(byte[] message) {
        int at = 0;
        long running;
        if (message.length >= 32) {
            long first = P64_1 + P64_2;
            long second = P64_2;
            long third = 0;
            long fourth = -P64_1;
            while (at + 32 <= message.length) {
                first = stirred64(first, longAt(message, at));
                second = stirred64(second, longAt(message, at + 8));
                third = stirred64(third, longAt(message, at + 16));
                fourth = stirred64(fourth, longAt(message, at + 24));
                at += 32;
            }
            running = Long.rotateLeft(first, 1) + Long.rotateLeft(second, 7)
                    + Long.rotateLeft(third, 12) + Long.rotateLeft(fourth, 18);
            running = foldedIn(running, first);
            running = foldedIn(running, second);
            running = foldedIn(running, third);
            running = foldedIn(running, fourth);
        } else {
            running = P64_5;
        }
        running += message.length;
        while (at + 8 <= message.length) {
            running = Long.rotateLeft(
                    running ^ stirred64(0, longAt(message, at)), 27) * P64_1 + P64_4;
            at += 8;
        }
        if (at + 4 <= message.length) {
            running = Long.rotateLeft(
                    running ^ (wordAt(message, at) & 0xFFFFFFFFL) * P64_1, 23)
                    * P64_2 + P64_3;
            at += 4;
        }
        while (at < message.length) {
            running = Long.rotateLeft(running ^ (message[at] & 0xFFL) * P64_5, 11) * P64_1;
            at++;
        }
        return scrambled64(running);
    }

    private static long stirred64(long accumulator, long word) {
        return Long.rotateLeft(accumulator + word * P64_2, 31) * P64_1;
    }

    /**
     * One accumulator folded into the running value.
     *
     * <p>Each is stirred once more before it goes in, so that a lane which
     * happened to end near nought does not simply leave the running value
     * alone.
     */
    private static long foldedIn(long running, long accumulator) {
        return (running ^ stirred64(0, accumulator)) * P64_1 + P64_4;
    }

    private static long scrambled64(long running) {
        long mixed = running ^ running >>> 33;
        mixed *= P64_2;
        mixed ^= mixed >>> 29;
        mixed *= P64_3;
        return mixed ^ mixed >>> 32;
    }

    private static int wordAt(byte[] bytes, int at) {
        return bytes[at] & 0xFF
                | (bytes[at + 1] & 0xFF) << 8
                | (bytes[at + 2] & 0xFF) << 16
                | (bytes[at + 3] & 0xFF) << 24;
    }

    private static long longAt(byte[] bytes, int at) {
        return wordAt(bytes, at) & 0xFFFFFFFFL
                | (long) wordAt(bytes, at + 4) << 32;
    }

    private static byte[] asBigEndian(long value, int width) {
        byte[] written = new byte[width];
        for (int at = 0; at < width; at++) {
            written[at] = (byte) (value >>> (width - 1 - at) * 8);
        }
        return written;
    }
}
