package org.jebol.domain.cipher;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Poly1305, a one-time authenticator.
 *
 * <p>RFC 8439 section 2.5, and {@code poly1305.c}. Not a hash and not a block
 * cipher: it evaluates a polynomial over a finite field, where the message
 * supplies the coefficients and the key supplies the point. The whole of it is
 * "add the next chunk, multiply by the key, repeat", modulo the prime two to
 * the hundred and thirtieth less five.
 *
 * <p>One-time is the load-bearing word. The key must never authenticate two
 * messages, because two tags under one key give away enough to forge a third.
 * The caller that uses it here is safe by construction: the key is a fresh
 * slice of a keystream that ChaCha20 has never produced before.
 *
 * <p>Written with whole numbers of unbounded size rather than packed into
 * machine words, because this runs over one record at a time and the clarity
 * is worth more than the speed. The C packs it into five limbs of twenty-six
 * bits and is much harder to read for it.
 */
public final class Poly1305 {

    private Poly1305() {
    }

    /** How many bytes of key, and of tag. */
    public static final int KEY = 32;

    private static final int CHUNK = 16;

    /** The prime the arithmetic is done modulo. */
    private static final BigInteger THE_PRIME =
            BigInteger.TWO.pow(130).subtract(BigInteger.valueOf(5));

    private static final BigInteger A_WHOLE_TAG = BigInteger.TWO.pow(128);

    /**
     * The tag over a message, under a key that must be used once.
     *
     * <p>The key is two halves doing different jobs: the first sixteen bytes
     * are the point the polynomial is evaluated at, and the last sixteen are
     * added at the end so that the answer cannot be worked backwards.
     */
    public static byte[] tagOf(byte[] key, byte[] message) {
        BigInteger point = theClampedPoint(key);
        BigInteger addedAtTheEnd = littleEndian(key, CHUNK, CHUNK);
        BigInteger running = BigInteger.ZERO;
        for (int at = 0; at < message.length; at += CHUNK) {
            int howMany = Math.min(CHUNK, message.length - at);
            running = running.add(theChunkAt(message, at, howMany));
            running = running.multiply(point).mod(THE_PRIME);
        }
        return sixteenLittleEndianOctets(
                running.add(addedAtTheEnd).mod(A_WHOLE_TAG));
    }

    /**
     * The first half of the key with certain bits cleared.
     *
     * <p>The clamping is not decoration: it keeps the point small enough that
     * the running total cannot overflow the field between one multiplication
     * and the next, which is what lets the C do this in five limbs at all.
     * Four bytes lose their top nibble and three lose their bottom two bits.
     */
    private static BigInteger theClampedPoint(byte[] key) {
        byte[] clamped = Arrays.copyOf(key, CHUNK);
        for (int at : new int[] {3, 7, 11, 15}) {
            clamped[at] &= 15;
        }
        for (int at : new int[] {4, 8, 12}) {
            clamped[at] &= (byte) 252;
        }
        return littleEndian(clamped, 0, CHUNK);
    }

    /**
     * One chunk of the message as a number, with a one written above it.
     *
     * <p>The extra one is what stops a chunk of noughts being the same as no
     * chunk at all, and what makes a short last chunk differ from the same
     * bytes padded out. It sits just above the bytes that are really there,
     * so a full chunk gets it at bit one hundred and twenty-eight and a chunk
     * of three bytes gets it at bit twenty-four.
     */
    private static BigInteger theChunkAt(byte[] message, int at, int howMany) {
        return littleEndian(message, at, howMany)
                .add(BigInteger.ONE.shiftLeft(howMany * Byte.SIZE));
    }

    private static BigInteger littleEndian(byte[] octets, int at, int howMany) {
        BigInteger number = BigInteger.ZERO;
        for (int step = howMany - 1; step >= 0; step--) {
            number = number.shiftLeft(Byte.SIZE)
                    .add(BigInteger.valueOf(octets[at + step] & 0xFF));
        }
        return number;
    }

    private static byte[] sixteenLittleEndianOctets(BigInteger number) {
        byte[] octets = new byte[CHUNK];
        BigInteger left = number;
        for (int at = 0; at < CHUNK; at++) {
            octets[at] = left.byteValue();
            left = left.shiftRight(Byte.SIZE);
        }
        return octets;
    }
}
