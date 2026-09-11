package org.jebol.domain.eval;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Counter with CBC-MAC, built out of the block cipher rather than found.
 *
 * <p>NIST SP 800-38C, and {@code ccm.c}. No JVM provider offers this mode, so
 * it is assembled here from the one thing the JVM does have: a single block of
 * AES. Everything below is that block called in two patterns.
 *
 * <p>The tag is a cipher-block-chaining message authentication code -- each
 * block is combined with the one before it and enciphered, so the last one
 * depends on every byte that went in. What goes in is a first block naming the
 * lengths and the settings, then the header, then the message, each padded out
 * to a whole block.
 *
 * <p>The cipher text is the message masked with a keystream, and the keystream
 * is the same block cipher run over a counter. The block at counter nought is
 * not used for the message: it masks the tag instead, which is what binds the
 * two halves together and stops a tag being moved from one message to another.
 *
 * <p>The nonce and the counter share sixteen bytes, so a longer nonce leaves
 * fewer bytes to count with. That is the whole reason the nonce is seven to
 * thirteen and not any length.
 */
final class CounterWithCbcMac {

    private CounterWithCbcMac() {
    }

    private static final int BLOCK = 16;

    private static final int SHORTEST_NONCE = 7;

    private static final int LONGEST_NONCE = 13;

    /**
     * The tag lengths this mode can name.
     *
     * <p>Three bits of the first authenticated block hold {@code (t - 2) / 2},
     * so only the even lengths from four to sixteen have a spelling. An odd
     * length is not a shorter tag, it is no tag at all.
     */
    static boolean canIssueATagOf(int octets) {
        return octets >= 4 && octets <= BLOCK && octets % 2 == 0;
    }

    /** What a run through the mode produced, or nothing where it could not. */
    record Sealed(byte[] octets, boolean worked) {

        static Sealed nothing() {
            return new Sealed(new byte[0], false);
        }
    }

    /**
     * Enciphers a message and answers it followed by its tag.
     *
     * <p>A tag length of nought is the starred form of the mode: the message
     * is counted through and nothing is authenticated, so the answer is the
     * cipher text alone.
     */
    static Sealed enciphered(byte[] key, byte[] vector, int tagOctets,
            byte[] header, byte[] message) {

        if (tagOctets != 0 && !canIssueATagOf(tagOctets)) {
            return Sealed.nothing();
        }
        byte[] nonce = nonceWithin(vector);
        try {
            Cipher block = theBlockCipher(key);
            byte[] cipherText = maskedWithTheKeystream(block, nonce, message);
            if (tagOctets == 0) {
                return new Sealed(cipherText, true);
            }
            byte[] tag = theTag(block, nonce, tagOctets, header, message);
            return new Sealed(joined(cipherText, tag), true);
        } catch (GeneralSecurityException refused) {
            return Sealed.nothing();
        }
    }

    /**
     * Deciphers a message and checks its tag, answering nothing when the two
     * disagree.
     *
     * <p>Checking here rather than handing the tag back is what separates this
     * mode from counting with Galois, and it means a caller cannot use plain
     * text that was never vouched for: {@code mbedtls_ccm_compare_tags} fails
     * the whole call and the port is left with nothing to read.
     */
    static Sealed deciphered(byte[] key, byte[] vector, int tagOctets,
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
        byte[] nonce = nonceWithin(vector);
        try {
            Cipher block = theBlockCipher(key);
            byte[] message = maskedWithTheKeystream(block, nonce, cipherText);
            if (tagOctets == 0) {
                return new Sealed(message, true);
            }
            byte[] tagComputed = theTag(block, nonce, tagOctets, header, message);
            return theyAgree(tagWritten, tagComputed)
                    ? new Sealed(message, true)
                    : Sealed.nothing();
        } catch (GeneralSecurityException refused) {
            return Sealed.nothing();
        }
    }

    /**
     * A comparison that takes the same time whichever byte disagrees.
     *
     * <p>Stopping at the first difference tells anybody timing the call how
     * much of their guess was right, which is how a tag gets found a byte at a
     * time. So every byte is looked at whatever the earlier ones said.
     */
    private static boolean theyAgree(byte[] written, byte[] computed) {
        if (written.length != computed.length) {
            return false;
        }
        int differences = 0;
        for (int at = 0; at < written.length; at++) {
            differences |= written[at] ^ computed[at];
        }
        return differences == 0;
    }

    private static Cipher theBlockCipher(byte[] key)
            throws GeneralSecurityException {

        Cipher block = Cipher.getInstance("AES/ECB/NoPadding");
        block.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return block;
    }

    /**
     * The nonce, brought to seven bytes at the short end and thirteen at the
     * long one.
     *
     * <p>{@code ctx->IV_len = MAX(7, MIN(13, ctx->IV_len))}, over a buffer
     * that was cleared first -- so a shorter vector is read with noughts after
     * it rather than refused, and a longer one has its tail ignored.
     */
    private static byte[] nonceWithin(byte[] vector) {
        return Arrays.copyOf(vector,
                Math.clamp(vector.length, SHORTEST_NONCE, LONGEST_NONCE));
    }

    /**
     * The message masked with the keystream, which both enciphers and
     * deciphers because masking twice with the same stream undoes itself.
     *
     * <p>Counting starts at one. The block at nought is kept back to mask the
     * tag.
     */
    private static byte[] maskedWithTheKeystream(
            Cipher block, byte[] nonce, byte[] message)
            throws GeneralSecurityException {

        byte[] masked = new byte[message.length];
        for (int at = 0; at < message.length; at += BLOCK) {
            byte[] keystream = block.doFinal(
                    counterBlock(nonce, at / BLOCK + 1));
            for (int within = 0; within < BLOCK && at + within < message.length;
                    within++) {
                masked[at + within] =
                        (byte) (message[at + within] ^ keystream[within]);
            }
        }
        return masked;
    }

    /**
     * A counter block: how many bytes the count takes, then the nonce, then
     * the count itself filling what is left.
     */
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

    /**
     * The tag: a chained code over the lengths, the header and the message,
     * masked with the block at counter nought.
     */
    private static byte[] theTag(Cipher block, byte[] nonce, int tagOctets,
            byte[] header, byte[] message) throws GeneralSecurityException {

        byte[] chained = block.doFinal(
                theFirstBlock(nonce, tagOctets, header.length, message.length));
        if (header.length > 0) {
            chained = chainedThrough(block, chained,
                    joined(theHeadersLengthWritten(header.length), header));
        }
        chained = chainedThrough(block, chained, message);
        byte[] maskedWith = block.doFinal(counterBlock(nonce, 0));
        byte[] tag = new byte[tagOctets];
        for (int at = 0; at < tagOctets; at++) {
            tag[at] = (byte) (chained[at] ^ maskedWith[at]);
        }
        return tag;
    }

    /**
     * The block every tag starts from: a byte of settings, the nonce, and how
     * long the message is.
     *
     * <p>The settings byte is where the tag length is spelled, as
     * {@code (t - 2) / 2} in three bits, which is why only the even lengths
     * exist. The top bit says whether there is a header at all.
     */
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

    /**
     * How long the header is, written in front of it.
     *
     * <p>Three spellings by size, because the length has to be told apart from
     * the header that follows it: two bytes up to just under sixty-five
     * thousand, then a marker and four bytes, then a marker and eight.
     */
    private static byte[] theHeadersLengthWritten(int octets) {
        if (octets < A_SHORT_HEADER) {
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

    /** Where two bytes stop being enough to say how long a header is. */
    private static final int A_SHORT_HEADER = 0xFF00;

    /**
     * Runs octets through the chain, a block at a time, padding the last one
     * with noughts.
     */
    private static byte[] chainedThrough(Cipher block, byte[] chained,
            byte[] octets) throws GeneralSecurityException {

        byte[] running = chained;
        for (int at = 0; at < octets.length; at += BLOCK) {
            byte[] combined = new byte[BLOCK];
            for (int within = 0; within < BLOCK; within++) {
                byte next = at + within < octets.length
                        ? octets[at + within]
                        : 0;
                combined[within] = (byte) (running[within] ^ next);
            }
            running = block.doFinal(combined);
        }
        return running;
    }

    private static byte[] joined(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }
}
