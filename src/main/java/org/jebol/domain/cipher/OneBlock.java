package org.jebol.domain.cipher;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

@FunctionalInterface
public interface OneBlock {

    byte[] encipher(byte[] block);

    static OneBlock useJvmAes(byte[] key) throws GeneralSecurityException {
        Cipher running = Cipher.getInstance("AES/ECB/NoPadding");
        running.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return block -> {
            try {
                return running.doFinal(block);
            } catch (GeneralSecurityException refused) {
                throw new IllegalStateException("a block the cipher had already accepted", refused);
            }
        };
    }
}
