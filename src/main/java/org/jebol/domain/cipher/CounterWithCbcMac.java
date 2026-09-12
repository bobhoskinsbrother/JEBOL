package org.jebol.domain.cipher;

import java.util.Arrays;

/**
 * Counter with CBC-MAC, NIST SP 800-38C, assembled here because no JVM
 * provider offers it.
 *
 * <p>The block at counter nought masks the tag rather than the message, which
 * is what binds the two halves together and stops a tag being moved from one
 * message to another. The nonce and the counter share sixteen bytes, which is
 * why the nonce is seven to thirteen and not any length.
 */
public final class CounterWithCbcMac {

    private CounterWithCbcMac() {
    }

    private static final int BLOCK = 16;

    private static final int SHORTEST_NONCE = 7;

    private static final int LONGEST_NONCE = 13;

    /**
     * The tag lengths this mode can name: three bits of the first
     * authenticated block hold {@code (t - 2) / 2}, so only the even lengths
     * from four to sixteen have a spelling.
     */
    public static boolean canIssueATagOf(int octets) {
        return octets >= 4 && octets <= BLOCK && octets % 2 == 0;
    }

    /** What a run through the mode produced, or nothing where it could not. */
    public record Sealed(byte[] octets, boolean worked) {

        static Sealed nothing() {
            return new Sealed(new byte[0], false);
        }
    }

    /**
     * Enciphers a message and answers it followed by its tag. A tag length of
     * nought is the starred form of the mode: nothing is authenticated and the
     * answer is the cipher text alone.
     */
    public static Sealed enciphered(OneBlock cipher, byte[] vector, int tagOctets,
            byte[] header, byte[] message) {

        if (tagOctets != 0 && !canIssueATagOf(tagOctets)) {
            return Sealed.nothing();
        }
        byte[] nonce = nonceClampedWithNoughtsAfterAShortOne(vector);
        byte[] cipherText = maskedWithTheKeystreamCountingFromOne(cipher, nonce, message);
        if (tagOctets == 0) {
            return new Sealed(cipherText, true);
        }
        byte[] tag = theTag(cipher, nonce, tagOctets, header, message);
        return new Sealed(joined(cipherText, tag), true);
    }

    /**
     * Deciphers a message and checks its tag, answering nothing when the two
     * disagree -- so a caller cannot reach plain text that was never vouched
     * for.
     */
    public static Sealed deciphered(OneBlock cipher, byte[] vector, int tagOctets,
            byte[] header, byte[] sealedOctets) {

        if (tagOctets != 0 && !canIssueATagOf(tagOctets)) {
            return Sealed.nothing();
        }
        if (sealedOctets.length < tagOctets) {
            return Sealed.nothing();
        }
        byte[] cipherText =
                Arrays.copyOf(sealedOctets, sealedOctets.length - tagOctets);
        byte[] tagWritten = Arrays.copyOfRange(
                sealedOctets, sealedOctets.length - tagOctets, sealedOctets.length);
        byte[] nonce = nonceClampedWithNoughtsAfterAShortOne(vector);
        byte[] message = maskedWithTheKeystreamCountingFromOne(cipher, nonce, cipherText);
        if (tagOctets == 0) {
            return new Sealed(message, true);
        }
        byte[] tagComputed = theTag(cipher, nonce, tagOctets, header, message);
        return theyAgreeInTheSameTimeWhicheverByteDisagrees(tagWritten, tagComputed)
                ? new Sealed(message, true)
                : Sealed.nothing();
    }

    private static boolean theyAgreeInTheSameTimeWhicheverByteDisagrees(
            byte[] written, byte[] computed) {
        if (written.length != computed.length) {
            return false;
        }
        int differences = 0;
        for (int at = 0; at < written.length; at++) {
            differences |= written[at] ^ computed[at];
        }
        return differences == 0;
    }

    private static byte[] nonceClampedWithNoughtsAfterAShortOne(byte[] vector) {
        return Arrays.copyOf(vector,
                Math.clamp(vector.length, SHORTEST_NONCE, LONGEST_NONCE));
    }

    private static byte[] maskedWithTheKeystreamCountingFromOne(
            OneBlock cipher, byte[] nonce, byte[] message) {

        byte[] masked = new byte[message.length];
        for (int at = 0; at < message.length; at += BLOCK) {
            byte[] keystream = cipher.enciphered(
                    counterBlock(nonce, at / BLOCK + 1));
            for (int within = 0; within < BLOCK && at + within < message.length;
                    within++) {
                masked[at + within] =
                        (byte) (message[at + within] ^ keystream[within]);
            }
        }
        return masked;
    }

    private static byte[] counterBlock(byte[] nonce, long count) {
        int countOctets = BLOCK - 1 - nonce.length;
        byte[] counter = new byte[BLOCK];
        counter[0] = (byte) (countOctets - 1);
        System.arraycopy(nonce, 0, counter, 1, nonce.length);
        long left = count;
        for (int at = BLOCK - 1; at > nonce.length; at--) {
            counter[at] = (byte) left;
            left >>>= 8;
        }
        return counter;
    }

    private static byte[] theTag(OneBlock cipher, byte[] nonce, int tagOctets,
            byte[] header, byte[] message) {

        byte[] chained = cipher.enciphered(
                theFirstBlock(nonce, tagOctets, header.length, message.length));
        if (header.length > 0) {
            chained = chainedThrough(cipher, chained,
                    joined(theHeadersLengthWritten(header.length), header));
        }
        chained = chainedThrough(cipher, chained, message);
        byte[] maskedWith = cipher.enciphered(counterBlock(nonce, 0));
        byte[] tag = new byte[tagOctets];
        for (int at = 0; at < tagOctets; at++) {
            tag[at] = (byte) (chained[at] ^ maskedWith[at]);
        }
        return tag;
    }

    private static byte[] theFirstBlock(byte[] nonce, int tagOctets,
            int headerOctets, int messageOctets) {

        int countOctets = BLOCK - 1 - nonce.length;
        byte[] first = new byte[BLOCK];
        first[0] = (byte) ((headerOctets > 0 ? 0x40 : 0)
                | (tagOctets - 2) / 2 << 3
                | countOctets - 1);
        System.arraycopy(nonce, 0, first, 1, nonce.length);
        long left = messageOctets;
        for (int at = BLOCK - 1; at > nonce.length; at--) {
            first[at] = (byte) left;
            left >>>= 8;
        }
        return first;
    }

    private static byte[] theHeadersLengthWritten(int octets) {
        if (octets < WHERE_TWO_BYTES_STOP_BEING_ENOUGH_FOR_A_HEADER) {
            return new byte[] {(byte) (octets >>> 8), (byte) octets};
        }
        byte[] written = new byte[6];
        written[0] = (byte) 0xFF;
        written[1] = (byte) 0xFE;
        for (int at = 5; at >= 2; at--) {
            written[at] = (byte) (octets >>> (5 - at) * 8);
        }
        return written;
    }

    private static final int WHERE_TWO_BYTES_STOP_BEING_ENOUGH_FOR_A_HEADER = 0xFF00;

    private static byte[] chainedThrough(OneBlock cipher, byte[] chained,
            byte[] octets) {

        byte[] running = chained;
        for (int at = 0; at < octets.length; at += BLOCK) {
            byte[] combined = new byte[BLOCK];
            for (int within = 0; within < BLOCK; within++) {
                byte next = at + within < octets.length
                        ? octets[at + within]
                        : 0;
                combined[within] = (byte) (running[within] ^ next);
            }
            running = cipher.enciphered(combined);
        }
        return running;
    }

    private static byte[] joined(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
