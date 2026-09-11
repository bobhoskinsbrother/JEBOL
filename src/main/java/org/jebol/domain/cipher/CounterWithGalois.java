package org.jebol.domain.cipher;

import java.util.Arrays;

/**
 * Counting with Galois, written over any block cipher rather than the JVM's.
 *
 * <p>NIST SP 800-38D, and {@code gcm.c}. The JVM has this mode for AES, but
 * only for AES and only in a shape that checks the tag for you -- and REBOL
 * hands the tag back for the caller to compare instead. Writing it out once
 * serves Camellia, which the JVM has not got, and drops the two-pass trick the
 * JVM's shape forced.
 *
 * <p>Two halves. The message is masked with a keystream made by counting, the
 * same as any counter mode. The tag is a running total in a finite field of
 * 128 bits, over the header and then the cipher text, finished with the two
 * lengths and masked with the block at the starting count.
 *
 * <p>The field is the awkward part and the only real arithmetic here: adding
 * is exclusive-or, and multiplying is the schoolbook shift-and-add with a
 * fold-back whenever a bit falls off the end.
 */
public final class CounterWithGalois {

    private CounterWithGalois() {
    }

    private static final int BLOCK = 16;

    /** How long a tag this mode computes before any of it is cut away. */
    public static final int WHOLE_TAG = 16;

    /** What a run through the mode produced. */
    public record Sealed(byte[] octets, byte[] tag) {
    }

    /**
     * Masks a message and computes its tag, in whichever direction.
     *
     * <p>The keystream is the same either way, which is what makes counting
     * its own inverse: enciphering a cipher text gives the plain text back.
     * Only the tag differs, because it is always taken over the cipher text --
     * so deciphering hashes what came in and enciphering hashes what goes out.
     */
    public static Sealed through(OneBlock cipher, byte[] vector, byte[] header,
            byte[] octets, boolean deciphering) {

        byte[] hashKey = cipher.enciphered(new byte[BLOCK]);
        byte[] startingCount = theStartingCount(cipher, hashKey, vector);
        byte[] masked = maskedFrom(cipher, startingCount, octets);
        byte[] overTheCipherText = deciphering ? octets : masked;

        byte[] running = new byte[BLOCK];
        running = hashedThrough(running, hashKey, header);
        running = hashedThrough(running, hashKey, overTheCipherText);
        running = hashedThrough(running, hashKey,
                theTwoLengths(header.length, overTheCipherText.length));

        byte[] maskedWith = cipher.enciphered(startingCount);
        byte[] tag = new byte[WHOLE_TAG];
        for (int at = 0; at < WHOLE_TAG; at++) {
            tag[at] = (byte) (running[at] ^ maskedWith[at]);
        }
        return new Sealed(masked, tag);
    }

    /**
     * Where the counting starts.
     *
     * <p>A twelve byte vector is the common case and becomes itself followed
     * by a count of one. Any other length has no room for that, so it is
     * hashed down to sixteen bytes instead -- which is why a vector of any
     * length works at all.
     */
    private static byte[] theStartingCount(
            OneBlock cipher, byte[] hashKey, byte[] vector) {

        if (vector.length == A_PLAIN_VECTOR) {
            byte[] counting = Arrays.copyOf(vector, BLOCK);
            counting[BLOCK - 1] = 1;
            return counting;
        }
        byte[] running = hashedThrough(new byte[BLOCK], hashKey, vector);
        return hashedThrough(running, hashKey, theTwoLengths(0, vector.length));
    }

    /** The vector length that needs no hashing, being the one GCM was built for. */
    private static final int A_PLAIN_VECTOR = 12;

    private static byte[] maskedFrom(
            OneBlock cipher, byte[] startingCount, byte[] octets) {

        byte[] masked = new byte[octets.length];
        byte[] counting = startingCount.clone();
        for (int at = 0; at < octets.length; at += BLOCK) {
            countOnce(counting);
            byte[] keystream = cipher.enciphered(counting);
            for (int within = 0;
                    within < BLOCK && at + within < octets.length; within++) {
                masked[at + within] =
                        (byte) (octets[at + within] ^ keystream[within]);
            }
        }
        return masked;
    }

    /** Adds one to the last four bytes, which is all the counting there is. */
    private static void countOnce(byte[] counting) {
        for (int at = BLOCK - 1; at >= BLOCK - 4; at--) {
            if (++counting[at] != 0) {
                return;
            }
        }
    }

    /** How long the header and the cipher text were, in bits, as one block. */
    private static byte[] theTwoLengths(int headerOctets, int cipherTextOctets) {
        byte[] lengths = new byte[BLOCK];
        putBitCount(lengths, 0, headerOctets);
        putBitCount(lengths, 8, cipherTextOctets);
        return lengths;
    }

    private static void putBitCount(byte[] into, int at, int octets) {
        long bits = (long) octets * Byte.SIZE;
        for (int step = 7; step >= 0; step--) {
            into[at + step] = (byte) bits;
            bits >>>= 8;
        }
    }

    /**
     * Runs octets into the running total, a block at a time, padding the last
     * one with noughts.
     */
    private static byte[] hashedThrough(
            byte[] running, byte[] hashKey, byte[] octets) {

        byte[] total = running;
        for (int at = 0; at < octets.length; at += BLOCK) {
            byte[] combined = new byte[BLOCK];
            for (int within = 0; within < BLOCK; within++) {
                byte next = at + within < octets.length ? octets[at + within] : 0;
                combined[within] = (byte) (total[within] ^ next);
            }
            total = multiplied(combined, hashKey);
        }
        return total;
    }

    /**
     * Multiplication in the field of 128 bits, which is shift and add with a
     * fold-back.
     *
     * <p>Adding is exclusive-or, so the schoolbook method is: for every bit of
     * one operand that is set, add the other; then shift the other along. When
     * a bit falls off the bottom the polynomial has overflowed, and it is
     * brought back in by adding the field's own constant. Bits are numbered
     * from the top here, which is the convention this standard uses and the
     * reason the shift goes the way it does.
     */
    private static byte[] multiplied(byte[] left, byte[] right) {
        byte[] product = new byte[BLOCK];
        byte[] running = right.clone();
        for (int bit = 0; bit < BLOCK * Byte.SIZE; bit++) {
            if ((left[bit / 8] >>> 7 - bit % 8 & 1) != 0) {
                for (int at = 0; at < BLOCK; at++) {
                    product[at] ^= running[at];
                }
            }
            boolean fellOff = (running[BLOCK - 1] & 1) != 0;
            shiftTowardsTheTop(running);
            if (fellOff) {
                running[0] ^= THE_FIELDS_OWN_CONSTANT;
            }
        }
        return product;
    }

    /**
     * What comes back in when a bit falls off, which is the polynomial this
     * field is built on written as its top byte.
     */
    private static final byte THE_FIELDS_OWN_CONSTANT = (byte) 0xE1;

    private static void shiftTowardsTheTop(byte[] octets) {
        for (int at = BLOCK - 1; at > 0; at--) {
            octets[at] = (byte) ((octets[at] & 0xFF) >>> 1
                    | (octets[at - 1] & 1) << 7);
        }
        octets[0] = (byte) ((octets[0] & 0xFF) >>> 1);
    }
}
