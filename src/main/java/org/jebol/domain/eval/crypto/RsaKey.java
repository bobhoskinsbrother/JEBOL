package org.jebol.domain.eval.crypto;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Optional;

public final class RsaKey {

    private final PublicKey publicHalf;
    private final PrivateKey privateHalf;

    private RsaKey(PublicKey publicHalf, PrivateKey privateHalf) {
        this.publicHalf = publicHalf;
        this.privateHalf = privateHalf;
    }

    public boolean canDecryptAndSign() {
        return privateHalf != null;
    }

    int modulusWidthInBytes() {
        return (((java.security.interfaces.RSAPublicKey) publicHalf)
                .getModulus().bitLength() + 7) / 8;
    }

    private static BigInteger readUnsignedMostSignificantByteFirst(byte[] octets) {
        return new BigInteger(1, octets);
    }

    public static Optional<RsaKey> makeAPublicKeyFrom(byte[] modulus, byte[] publicExponent) {
        try {
            BigInteger n = readUnsignedMostSignificantByteFirst(modulus);
            BigInteger e = readUnsignedMostSignificantByteFirst(publicExponent);
            if (n.signum() <= 0 || e.signum() <= 0 || !n.testBit(0) || n.bitLength() < 16) {
                return Optional.empty();
            }
            return Optional.of(new RsaKey(KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(n, e)), null));
        } catch (java.security.GeneralSecurityException | RuntimeException notAKey) {
            return Optional.empty();
        }
    }

    public static Optional<RsaKey> makeAPrivateKeyFrom(byte[] modulus, byte[] publicExponent,
                                                       byte[] privateExponent, byte[] firstPrime, byte[] secondPrime) {
        try {
            BigInteger n = readUnsignedMostSignificantByteFirst(modulus);
            BigInteger e = readUnsignedMostSignificantByteFirst(publicExponent);
            BigInteger d = readUnsignedMostSignificantByteFirst(privateExponent);
            BigInteger p = readUnsignedMostSignificantByteFirst(firstPrime);
            BigInteger q = readUnsignedMostSignificantByteFirst(secondPrime);
            if (p.signum() <= 0 || q.signum() <= 0 || !p.multiply(q).equals(n)) {
                return Optional.empty();
            }
            BigInteger one = BigInteger.ONE;
            if (!doTheExponentsAgree(e, d, p, q)) {
                return Optional.empty();
            }
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey privateHalf = factory.generatePrivate(new RSAPrivateCrtKeySpec(
                    n, e, d,
                    p, q,
                    d.mod(p.subtract(one)),
                    d.mod(q.subtract(one)),
                    q.modInverse(p)));
            return Optional.of(new RsaKey(
                    factory.generatePublic(new RSAPublicKeySpec(n, e)), privateHalf));
        } catch (java.security.GeneralSecurityException | RuntimeException notAKey) {
            return Optional.empty();
        }
    }

    private static boolean doTheExponentsAgree(
            BigInteger e, BigInteger d, BigInteger p, BigInteger q) {

        BigInteger one = BigInteger.ONE;
        BigInteger belowP = p.subtract(one);
        BigInteger belowQ = q.subtract(one);
        BigInteger smallestPeriod = belowP.divide(belowP.gcd(belowQ)).multiply(belowQ);
        return e.multiply(d).mod(smallestPeriod).equals(one);
    }

    private static String nameTheCipher(
            boolean optimalPadding) {
        return optimalPadding
                ? "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
                : "RSA/ECB/PKCS1Padding";
    }

    public byte[] encipher(byte[] data, boolean optimalPadding) throws Exception {
        Cipher cipher = Cipher.getInstance(
                nameTheCipher(optimalPadding));
        cipher.init(Cipher.ENCRYPT_MODE, publicHalf);
        return cipher.doFinal(data);
    }

    public byte[] decipher(byte[] data, boolean optimalPadding) throws Exception {
        Cipher cipher = Cipher.getInstance(
                nameTheCipher(optimalPadding));
        cipher.init(Cipher.DECRYPT_MODE, privateHalf);
        return cipher.doFinal(data);
    }

    private static String pickTheDigestOrSha256(String asked) {
        return switch (asked) {
            case "md5" -> "MD5";
            case "sha1" -> "SHA-1";
            case "sha224" -> "SHA-224";
            case "sha384" -> "SHA-384";
            case "sha512" -> "SHA-512";
            default -> "SHA-256";
        };
    }

    private static Signature makeASignatureFor(String digest, boolean probabilistic)
            throws java.security.GeneralSecurityException {
        String algorithm = pickTheDigestOrSha256(digest);
        if (!probabilistic) {
            return Signature.getInstance(algorithm.replace("-", "") + "withRSA");
        }
        Signature scheme = Signature.getInstance("RSASSA-PSS");
        scheme.setParameter(new java.security.spec.PSSParameterSpec(
                algorithm, "MGF1",
                new java.security.spec.MGF1ParameterSpec(algorithm),
                measureTheSaltAsLongAsTheDigest(algorithm),
                java.security.spec.PSSParameterSpec.TRAILER_FIELD_BC));
        return scheme;
    }

    private static int measureTheSaltAsLongAsTheDigest(String algorithm)
            throws java.security.NoSuchAlgorithmException {
        return java.security.MessageDigest.getInstance(algorithm).getDigestLength();
    }

    public byte[] sign(byte[] data, String digest, boolean probabilistic) throws Exception {
        Signature signing = makeASignatureFor(digest, probabilistic);
        signing.initSign(privateHalf);
        signing.update(data);
        return signing.sign();
    }

    public boolean verifies(byte[] data, byte[] signature, String digest, boolean probabilistic) {
        try {
            Signature checking = makeASignatureFor(digest, probabilistic);
            checking.initVerify(publicHalf);
            checking.update(data);
            return checking.verify(signature);
        } catch (java.security.GeneralSecurityException | RuntimeException doesNotHold) {
            return false;
        }
    }
}
