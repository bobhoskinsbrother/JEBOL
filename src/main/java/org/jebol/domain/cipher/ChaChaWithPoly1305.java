package org.jebol.domain.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

public final class ChaChaWithPoly1305 {

    private ChaChaWithPoly1305() {
    }

    private static final int CHUNK = 16;

    public static final int NONCE = 12;

    private static final int AUTHENTICATORS_BLOCK = 0;

    private static final int WHERE_MESSAGE_STARTS = 1;

    private static final int SEQUENCE_NUMBER = 8;

    private static final int OCTETS_IN_SIXTY_FOUR_BIT_LENGTH = 8;

    public record Sealed(byte[] octets, byte[] tag) {
    }

    public static Sealed seal(byte[] key, byte[] nonce, byte[] header, byte[] octets, boolean deciphering) {
        try {
            byte[] authenticatorKey = generateKeystream(key, nonce, AUTHENTICATORS_BLOCK, Poly1305.KEY);
            byte[] masked = maskWith(generateKeystream(
                    key, nonce, WHERE_MESSAGE_STARTS, octets.length), octets);
            byte[] overCipherText = deciphering ? octets : masked;
            return new Sealed(masked, Poly1305.computeTag(authenticatorKey,
                    collectAuthenticated(header, overCipherText)));
        } catch (GeneralSecurityException refused) {
            return new Sealed(new byte[0], new byte[0]);
        }
    }

    private static byte[] collectAuthenticated(
            byte[] header, byte[] cipherText) {
        byte[] joined = new byte[padLength(header.length)
                + padLength(cipherText.length) + CHUNK];
        System.arraycopy(header, 0, joined, 0, header.length);
        int at = padLength(header.length);
        System.arraycopy(cipherText, 0, joined, at, cipherText.length);
        at = joined.length - CHUNK;
        putLittleEndian(joined, at, header.length);
        putLittleEndian(joined, at + OCTETS_IN_SIXTY_FOUR_BIT_LENGTH,
                cipherText.length);
        return joined;
    }

    private static int padLength(int octets) {
        return octets % CHUNK == 0 ? octets : octets + CHUNK - octets % CHUNK;
    }

    private static void putLittleEndian(byte[] into, int at, int howMany) {
        long left = howMany;
        for (int step = 0; step < OCTETS_IN_SIXTY_FOUR_BIT_LENGTH; step++) {
            into[at + step] = (byte) left;
            left >>>= Byte.SIZE;
        }
    }

    public static byte[] makeNonce(byte[] vector, byte[] header) {
        byte[] nonce = Arrays.copyOf(vector, NONCE);
        int howMany = Math.min(header.length, SEQUENCE_NUMBER);
        for (int at = 0; at < howMany; at++) {
            nonce[NONCE - howMany + at] ^= header[at];
        }
        return nonce;
    }

    private static byte[] generateKeystream(
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

    private static byte[] maskWith(byte[] keystream, byte[] octets) {
        byte[] masked = new byte[octets.length];
        for (int at = 0; at < octets.length; at++) {
            masked[at] = (byte) (octets[at] ^ keystream[at]);
        }
        return masked;
    }
}
