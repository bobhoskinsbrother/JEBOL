package org.jebol.domain.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * A block cipher, asked for one block at a time and nothing else -- the whole
 * of what a mode needs, so adding a cipher adds every mode with it.
 */
@FunctionalInterface
public interface OneBlock {

    /** Sixteen bytes in, sixteen out. */
    byte[] enciphered(byte[] block);

    /** The JVM's own AES, transforming one block and remembering nothing. */
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
