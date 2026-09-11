package org.jebol.domain.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * A block cipher, asked for one block at a time and nothing else.
 *
 * <p>This is the whole of what a mode needs. Codebook, chaining, counting with
 * a tag -- none of them knows what cipher they are driving beyond being able
 * to hand it sixteen bytes and get sixteen back, which is why adding a cipher
 * adds every mode with it rather than needing each one written again.
 *
 * <p>Two of these exist. One asks the JVM, which has AES and DES. The other is
 * {@link Camellia}, which the JVM has not got and which is written out here.
 * Nothing above this line can tell them apart.
 */
@FunctionalInterface
public interface OneBlock {

    /** Sixteen bytes in, sixteen out. */
    byte[] enciphered(byte[] block);

    /**
     * The JVM's own AES in its simplest arrangement, which is the one that
     * transforms a single block and remembers nothing between calls.
     */
    static OneBlock jvmAes(byte[] key) throws GeneralSecurityException {
        Cipher running = Cipher.getInstance("AES/ECB/NoPadding");
        running.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return block -> {
            try {
                return running.doFinal(block);
            } catch (GeneralSecurityException refused) {
                throw new IllegalStateException(
                        "a block the cipher had already accepted", refused);
            }
        };
    }
}
