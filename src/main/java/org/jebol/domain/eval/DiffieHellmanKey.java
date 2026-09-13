package org.jebol.domain.eval;

import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.DHPublicKeySpec;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Optional;

final class DiffieHellmanKey implements AKeyThatCanBeReleased {

    private boolean handedBack;

    @Override
    public void release() {
        handedBack = true;
    }

    @Override
    public boolean released() {
        return handedBack;
    }

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

    byte[] publishedPaddedToTheWidthOfThePrime() {
        return fixedWidth(((javax.crypto.interfaces.DHPublicKey) pair.getPublic())
                .getY());
    }

    Optional<byte[]> agreedWith(byte[] peersPublicValue) {
        if (handedBack) {
            return Optional.empty();
        }
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
