package org.jebol.domain.cipher;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Poly1305, a one-time authenticator. RFC 8439 section 2.5.
 *
 * <p>One-time is the load-bearing word: the key must never authenticate two
 * messages, because two tags under one key give away enough to forge a third.
 */
public final class Poly1305 {

    private Poly1305() {
    }

    /** How many bytes of key, and of tag. */
    public static final int KEY = 32;

    private static final int CHUNK = 16;

    private static final BigInteger THE_PRIME_THE_ARITHMETIC_IS_DONE_MODULO =
            BigInteger.TWO.pow(130).subtract(BigInteger.valueOf(5));

    private static final BigInteger A_WHOLE_TAG = BigInteger.TWO.pow(128);

    /** The tag over a message, under a key that must be used once. */
    public static byte[] tagOf(byte[] key, byte[] message) {
        BigInteger thePointThePolynomialIsEvaluatedAt = theClampedPoint(key);
        BigInteger addedAtTheEndSoTheAnswerCannotBeWorkedBackwards =
                littleEndian(key, CHUNK, CHUNK);
        BigInteger running = BigInteger.ZERO;
        for (int at = 0; at < message.length; at += CHUNK) {
            int howMany = Math.min(CHUNK, message.length - at);
            running = running.add(theChunkAt(message, at, howMany));
            running = running.multiply(thePointThePolynomialIsEvaluatedAt)
                    .mod(THE_PRIME_THE_ARITHMETIC_IS_DONE_MODULO);
        }
        return sixteenLittleEndianOctets(
                running.add(addedAtTheEndSoTheAnswerCannotBeWorkedBackwards)
                        .mod(A_WHOLE_TAG));
    }

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

    private static BigInteger theChunkAt(byte[] message, int at, int howMany) {
        return littleEndian(message, at, howMany)
                .add(aOneJustAboveTheBytesThatAreReallyThere(howMany));
    }

    private static BigInteger aOneJustAboveTheBytesThatAreReallyThere(int howMany) {
        return BigInteger.ONE.shiftLeft(howMany * Byte.SIZE);
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
