package org.jebol.domain.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * ChaCha20 joined to Poly1305, RFC 8439 section 2.8.
 *
 * <p>The authenticator's key is the first thirty-two bytes of the cipher's own
 * keystream, taken from the block before the one the message starts at -- so
 * every message gets a key that has never existed before, which is what makes a
 * one-time authenticator safe to use more than once.
 */
public final class ChaChaWithPoly1305 {

    private ChaChaWithPoly1305() {
    }

    private static final int CHUNK = 16;

    /** How long the nonce is, and where the message's counting starts. */
    public static final int NONCE = 12;

    private static final int THE_AUTHENTICATORS_BLOCK = 0;

    private static final int WHERE_THE_MESSAGE_STARTS = 1;

    private static final int A_SEQUENCE_NUMBER = 8;

    private static final int OCTETS_IN_A_SIXTY_FOUR_BIT_LENGTH = 8;

    /** What a run through it produced. */
    public record Sealed(byte[] octets, byte[] tag) {
    }

    /**
     * Masks a message and authenticates it, in whichever direction. The masking
     * is the same either way, because counting is its own inverse; only what
     * gets authenticated differs, and it is always the cipher text.
     */
    public static Sealed through(byte[] key, byte[] nonce, byte[] header,
            byte[] octets, boolean deciphering) {

        try {
            byte[] authenticatorKey = keystreamFrom(
                    key, nonce, THE_AUTHENTICATORS_BLOCK, Poly1305.KEY);
            byte[] masked = maskedWith(keystreamFrom(
                    key, nonce, WHERE_THE_MESSAGE_STARTS, octets.length), octets);
            byte[] overTheCipherText = deciphering ? octets : masked;
            return new Sealed(masked, Poly1305.tagOf(authenticatorKey,
                    whatGetsAuthenticated(header, overTheCipherText)));
        } catch (GeneralSecurityException refused) {
            return new Sealed(new byte[0], new byte[0]);
        }
    }

    private static byte[] whatGetsAuthenticated(
            byte[] header, byte[] cipherText) {

        byte[] joined = new byte[paddedLength(header.length)
                + paddedLength(cipherText.length) + CHUNK];
        System.arraycopy(header, 0, joined, 0, header.length);
        int at = paddedLength(header.length);
        System.arraycopy(cipherText, 0, joined, at, cipherText.length);
        at = joined.length - CHUNK;
        putLittleEndian(joined, at, header.length);
        putLittleEndian(joined, at + OCTETS_IN_A_SIXTY_FOUR_BIT_LENGTH,
                cipherText.length);
        return joined;
    }

    private static int paddedLength(int octets) {
        return octets % CHUNK == 0 ? octets : octets + CHUNK - octets % CHUNK;
    }

    private static void putLittleEndian(byte[] into, int at, int howMany) {
        long left = howMany;
        for (int step = 0; step < OCTETS_IN_A_SIXTY_FOUR_BIT_LENGTH; step++) {
            into[at + step] = (byte) left;
            left >>>= Byte.SIZE;
        }
    }

    /**
     * The nonce REBOL uses: the vector with the head of the record folded into
     * its tail. In TLS the first eight bytes of a record's header are its
     * sequence number, so every record under one key gets a nonce of its own
     * without either end sending one.
     */
    public static byte[] nonceFrom(byte[] vector, byte[] header) {
        byte[] nonce = Arrays.copyOf(vector, NONCE);
        int howMany = Math.min(header.length, A_SEQUENCE_NUMBER);
        for (int at = 0; at < howMany; at++) {
            nonce[NONCE - howMany + at] ^= header[at];
        }
        return nonce;
    }

    private static byte[] keystreamFrom(
            byte[] key, byte[] nonce, int countingFrom, int howMany)
            throws GeneralSecurityException {

        if (howMany == 0) {
            return new byte[0];
        }
        Cipher counting = Cipher.getInstance("ChaCha20");
        counting.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"),
                new ChaCha20ParameterSpec(nonce, countingFrom));
        return counting.doFinal(new byte[howMany]);
    }

    private static byte[] maskedWith(byte[] keystream, byte[] octets) {
        byte[] masked = new byte[octets.length];
        for (int at = 0; at < octets.length; at++) {
            masked[at] = (byte) (octets[at] ^ keystream[at]);
        }
        return masked;
    }
}
