package org.jebol.domain.cipher;

import java.util.Arrays;

public final class CounterWithGalois {

    private CounterWithGalois() {
    }

    private static final int BLOCK = 16;
    public static final int WHOLE_TAG = 16;
    private static final byte THE_FIELDS_OWN_CONSTANT = (byte) 0xE1;

    public record Sealed(byte[] octets, byte[] tag) {
    }

    public static Sealed seal(OneBlock cipher, byte[] vector, byte[] header,
                              byte[] octets, boolean deciphering) {

        byte[] hashKey = cipher.encipher(new byte[BLOCK]);
        byte[] startingCount = computeTheStartingCount(cipher, hashKey, vector);
        byte[] masked = maskFrom(cipher, startingCount, octets);
        byte[] overTheCipherText = deciphering ? octets : masked;

        byte[] running = new byte[BLOCK];
        running = hashThrough(running, hashKey, header);
        running = hashThrough(running, hashKey, overTheCipherText);
        running = hashThrough(running, hashKey,
                packTwoLengthsInBits(header.length, overTheCipherText.length));

        byte[] maskedWith = cipher.encipher(startingCount);
        byte[] tag = new byte[WHOLE_TAG];
        for (int at = 0; at < WHOLE_TAG; at++) {
            tag[at] = (byte) (running[at] ^ maskedWith[at]);
        }
        return new Sealed(masked, tag);
    }

    private static byte[] computeTheStartingCount(
            OneBlock cipher, byte[] hashKey, byte[] vector) {

        if (vector.length == THE_VECTOR_LENGTH_THAT_NEEDS_NO_HASHING) {
            byte[] counting = Arrays.copyOf(vector, BLOCK);
            counting[BLOCK - 1] = 1;
            return counting;
        }
        byte[] running = hashThrough(new byte[BLOCK], hashKey, vector);
        return hashThrough(running, hashKey, packTwoLengthsInBits(0, vector.length));
    }

    private static final int THE_VECTOR_LENGTH_THAT_NEEDS_NO_HASHING = 12;

    private static final int OCTETS_THE_COUNT_OCCUPIES = 4;

    private static final int OCTETS_IN_A_SIXTY_FOUR_BIT_LENGTH = 8;

    private static byte[] maskFrom(
            OneBlock cipher, byte[] startingCount, byte[] octets) {

        byte[] masked = new byte[octets.length];
        byte[] counting = startingCount.clone();
        for (int at = 0; at < octets.length; at += BLOCK) {
            countOnce(counting);
            byte[] keystream = cipher.encipher(counting);
            for (int within = 0;
                    within < BLOCK && at + within < octets.length; within++) {
                masked[at + within] =
                        (byte) (octets[at + within] ^ keystream[within]);
            }
        }
        return masked;
    }

    private static void countOnce(byte[] counting) {
        for (int at = BLOCK - 1; at >= BLOCK - OCTETS_THE_COUNT_OCCUPIES; at--) {
            if (++counting[at] != 0) {
                return;
            }
        }
    }

    private static byte[] packTwoLengthsInBits(
            int headerOctets, int cipherTextOctets) {

        byte[] lengths = new byte[BLOCK];
        putBitCount(lengths, 0, headerOctets);
        putBitCount(lengths, OCTETS_IN_A_SIXTY_FOUR_BIT_LENGTH, cipherTextOctets);
        return lengths;
    }

    private static void putBitCount(byte[] into, int at, int octets) {
        long bits = (long) octets * Byte.SIZE;
        for (int step = OCTETS_IN_A_SIXTY_FOUR_BIT_LENGTH - 1; step >= 0; step--) {
            into[at + step] = (byte) bits;
            bits >>>= Byte.SIZE;
        }
    }

    private static byte[] hashThrough(
            byte[] running, byte[] hashKey, byte[] octets) {

        byte[] total = running;
        for (int at = 0; at < octets.length; at += BLOCK) {
            byte[] combined = new byte[BLOCK];
            for (int within = 0; within < BLOCK; within++) {
                byte next = at + within < octets.length ? octets[at + within] : 0;
                combined[within] = (byte) (total[within] ^ next);
            }
            total = multiply(combined, hashKey);
        }
        return total;
    }

    private static byte[] multiply(byte[] left, byte[] right) {
        byte[] product = new byte[BLOCK];
        byte[] running = right.clone();
        for (int bit = 0; bit < BLOCK * Byte.SIZE; bit++) {
            if ((left[bit / 8] >>> 7 - bit % 8 & 1) != 0) {
                for (int at = 0; at < BLOCK; at++) {
                    product[at] ^= running[at];
                }
            }
            boolean fellOff = (running[BLOCK - 1] & 1) != 0;
            shiftTowardsTop(running);
            if (fellOff) {
                running[0] ^= THE_FIELDS_OWN_CONSTANT;
            }
        }
        return product;
    }

    private static void shiftTowardsTop(byte[] octets) {
        for (int at = BLOCK - 1; at > 0; at--) {
            octets[at] = (byte) ((octets[at] & 0xFF) >>> 1
                    | (octets[at - 1] & 1) << 7);
        }
        octets[0] = (byte) ((octets[0] & 0xFF) >>> 1);
    }
}
