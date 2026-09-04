package org.jebol.domain.eval;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Optional;

/**
 * One side of a Diffie-Hellman exchange: a generated key pair whose private
 * half never leaves.
 *
 * <p>Two parties who have never met need a shared secret over a line anyone
 * can read. Each makes a private number, publishes something derived from it,
 * and combines the other's published value with their own private one. Both
 * arrive at the same secret, and a listener who saw both published values
 * cannot work it out.
 *
 * <p>That is why the context here is unlike RSA's. An RSA context holds a key
 * the caller supplied; this holds a key the interpreter generated, and the
 * only things a caller can do with it are publish and agree.
 */
final class DiffieHellmanKey {

    /**
     * What the C will accept as a field prime, in bytes.
     *
     * <p>{@code if (n < 64 || n > 512) goto error;} -- below the lower bound
     * the exchange is not worth performing, and above the upper one it is
     * slower than anything reasonable wants.
     */
    private static final int NARROWEST_PRIME = 64;
    private static final int WIDEST_PRIME = 512;

    private final KeyPair pair;
    private final BigInteger fieldPrime;
    private final int widthInBytes;

    private DiffieHellmanKey(KeyPair pair, BigInteger fieldPrime, int widthInBytes) {
        this.pair = pair;
        this.fieldPrime = fieldPrime;
        this.widthInBytes = widthInBytes;
    }

    /**
     * A fresh key pair for these parameters, or nothing when they will not
     * carry an exchange.
     */
    static Optional<DiffieHellmanKey> generatedFor(byte[] generator, byte[] prime) {
        try {
            BigInteger p = new BigInteger(1, prime);
            BigInteger g = new BigInteger(1, generator);
            int width = (p.bitLength() + 7) / 8;
            if (width < NARROWEST_PRIME || width > WIDEST_PRIME
                    || g.signum() <= 0 || g.compareTo(p) >= 0) {
                return Optional.empty();
            }
            KeyPairGenerator generating = KeyPairGenerator.getInstance("DH");
            generating.initialize(new DHParameterSpec(p, g));
            return Optional.of(new DiffieHellmanKey(
                    generating.generateKeyPair(), p, width));
        } catch (java.security.GeneralSecurityException | RuntimeException unusable) {
            return Optional.empty();
        }
    }

    /**
     * The value to send, padded to the width of the field prime.
     *
     * <p>Padded because the peer reads it as a fixed-width number and a
     * leading zero byte would otherwise be dropped, giving a value one byte
     * short every few exchanges and a secret that does not agree.
     */
    byte[] published() {
        return fixedWidth(((javax.crypto.interfaces.DHPublicKey) pair.getPublic())
                .getY());
    }

    /**
     * The secret both sides reach, or nothing when the peer's value is not a
     * usable one.
     */
    Optional<byte[]> agreedWith(byte[] peersPublicValue) {
        try {
            BigInteger theirs = new BigInteger(1, peersPublicValue);
            if (theirs.signum() <= 0 || theirs.compareTo(fieldPrime) >= 0) {
                return Optional.empty();
            }
            DHParameterSpec parameters = ((javax.crypto.interfaces.DHPublicKey)
                    pair.getPublic()).getParams();
            KeyAgreement agreeing = KeyAgreement.getInstance("DH");
            agreeing.init(pair.getPrivate());
            agreeing.doPhase(java.security.KeyFactory.getInstance("DH")
                    .generatePublic(new DHPublicKeySpec(theirs,
                            parameters.getP(), parameters.getG())), true);
            return Optional.of(fixedWidth(new BigInteger(1, agreeing.generateSecret())));
        } catch (java.security.GeneralSecurityException | RuntimeException cannotAgree) {
            return Optional.empty();
        }
    }

    private byte[] fixedWidth(BigInteger value) {
        byte[] written = new byte[widthInBytes];
        byte[] raw = value.toByteArray();
        int from = raw.length > widthInBytes ? raw.length - widthInBytes : 0;
        int taking = Math.min(raw.length, widthInBytes);
        System.arraycopy(raw, from, written, widthInBytes - taking, taking);
        return written;
    }
}
