package org.jebol.domain.cipher;

import java.math.BigInteger;
import java.util.Arrays;

public final class Poly1305 {

    private Poly1305() {
    }

    public static final int KEY = 32;
    private static final int CHUNK = 16;
    private static final BigInteger PRIME_ARITHMETIC_IS_DONE_MODULO =
            BigInteger.TWO.pow(130).subtract(BigInteger.valueOf(5));
    private static final BigInteger WHOLE_TAG = BigInteger.TWO.pow(128);

    /** The tag over a message, under a key that must be used once. */
    public static byte[] computeTag(byte[] key, byte[] message) {
        BigInteger pointPolynomialIsEvaluatedAt = clampPoint(key);
        BigInteger addedAtEndSoAnswerCannotBeWorkedBackwards = readLittleEndian(key, CHUNK, CHUNK);
        BigInteger running = BigInteger.ZERO;
        for (int at = 0; at < message.length; at += CHUNK) {
            int howMany = Math.min(CHUNK, message.length - at);
            running = running.add(readChunk(message, at, howMany));
            running = running.multiply(pointPolynomialIsEvaluatedAt).mod(PRIME_ARITHMETIC_IS_DONE_MODULO);
        }
        return writeSixteenLittleEndianOctets(
                running.add(addedAtEndSoAnswerCannotBeWorkedBackwards)
                        .mod(WHOLE_TAG));
    }

    private static BigInteger clampPoint(byte[] key) {
        byte[] clamped = Arrays.copyOf(key, CHUNK);
        for (int at : new int[] {3, 7, 11, 15}) {
            clamped[at] &= 15;
        }
        for (int at : new int[] {4, 8, 12}) {
            clamped[at] &= (byte) 252;
        }
        return readLittleEndian(clamped, 0, CHUNK);
    }

    private static BigInteger readChunk(byte[] message, int at, int howMany) {
        return readLittleEndian(message, at, howMany)
                .add(placeOneAboveBytes(howMany));
    }

    private static BigInteger placeOneAboveBytes(int howMany) {
        return BigInteger.ONE.shiftLeft(howMany * Byte.SIZE);
    }

    private static BigInteger readLittleEndian(byte[] octets, int at, int howMany) {
        BigInteger number = BigInteger.ZERO;
        for (int step = howMany - 1; step >= 0; step--) {
            number = number.shiftLeft(Byte.SIZE)
                    .add(BigInteger.valueOf(octets[at + step] & 0xFF));
        }
        return number;
    }

    private static byte[] writeSixteenLittleEndianOctets(BigInteger number) {
        byte[] octets = new byte[CHUNK];
        BigInteger left = number;
        for (int at = 0; at < CHUNK; at++) {
            octets[at] = left.byteValue();
            left = left.shiftRight(Byte.SIZE);
        }
        return octets;
    }
}
