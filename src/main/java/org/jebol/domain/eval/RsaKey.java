package org.jebol.domain.eval;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Optional;
import javax.crypto.Cipher;

/**
 * An RSA key, checked once when it is built and then used for as many
 * operations as a caller wants.
 *
 * <p>{@code n-crypt.c} builds this in RSA-INIT and uses it in RSA, and the
 * split is about cost and about where a caller learns the numbers were wrong.
 * The key arrives as raw numbers, each a binary, and turning those into
 * something that can encipher means checking they really are a key: that the
 * modulus and exponent agree, and for a private key that the primes multiply
 * back to the modulus. Doing that per operation would repeat the expensive
 * part of every call.
 *
 * <p>Numbers that are not a key give an empty answer rather than throwing.
 * {@code mbedtls_rsa_check_pubkey} failing is {@code return R_NONE} in the C,
 * and the native turns that into none -- which is the opposite of what the
 * rest of the family does, and the reason it is worth having in one place.
 */
final class RsaKey {

    /**
     * The exponents the C's private path needs, and nothing else.
     *
     * <p>The C imports the modulus, both primes, the private exponent and the
     * public exponent, then calls {@code mbedtls_rsa_complete} to work out
     * the rest. The JDK wants the same set plus three numbers derived from
     * them, so they are derived here for the same reason mbedtls derives
     * them: a caller has the five and should not have to supply eight.
     */
    private final PublicKey publicHalf;
    private final PrivateKey privateHalf;

    private RsaKey(PublicKey publicHalf, PrivateKey privateHalf) {
        this.publicHalf = publicHalf;
        this.privateHalf = privateHalf;
    }

    boolean canDecryptAndSign() {
        return privateHalf != null;
    }

    int modulusWidthInBytes() {
        return (((java.security.interfaces.RSAPublicKey) publicHalf)
                .getModulus().bitLength() + 7) / 8;
    }

    /** A number as the C reads one: unsigned, most significant byte first. */
    private static BigInteger unsigned(byte[] octets) {
        return new BigInteger(1, octets);
    }

    /**
     * A public key, or nothing when the numbers do not form one.
     *
     * <p>An even modulus, a zero modulus and an exponent with no relation to
     * it all arrive here and all have to come back as nothing rather than as
     * a key that fails later.
     */
    static Optional<RsaKey> publicKeyFrom(byte[] modulus, byte[] publicExponent) {
        try {
            BigInteger n = unsigned(modulus);
            BigInteger e = unsigned(publicExponent);
            if (n.signum() <= 0 || e.signum() <= 0 || !n.testBit(0) || n.bitLength() < 16) {
                return Optional.empty();
            }
            return Optional.of(new RsaKey(KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(n, e)), null));
        } catch (java.security.GeneralSecurityException | RuntimeException notAKey) {
            return Optional.empty();
        }
    }

    /**
     * A key that can decrypt and sign, or nothing when the numbers do not
     * form one.
     *
     * <p>The primes are checked against the modulus rather than trusted:
     * {@code mbedtls_rsa_check_privkey} is what makes a wrong private
     * exponent answer none instead of enciphering nonsense. They may arrive
     * either way round, because p times q is q times p and the key is the
     * same key.
     */
    static Optional<RsaKey> privateKeyFrom(byte[] modulus, byte[] publicExponent,
            byte[] privateExponent, byte[] firstPrime, byte[] secondPrime) {
        try {
            BigInteger n = unsigned(modulus);
            BigInteger e = unsigned(publicExponent);
            BigInteger d = unsigned(privateExponent);
            BigInteger p = unsigned(firstPrime);
            BigInteger q = unsigned(secondPrime);
            if (p.signum() <= 0 || q.signum() <= 0 || !p.multiply(q).equals(n)) {
                return Optional.empty();
            }
            // Against the Carmichael function rather than Euler's totient.
            // A generator is free to choose the private exponent modulo
            // either, and the JDK's own chooses the smaller -- so checking
            // e*d against (p-1)(q-1) turns away perfectly good keys,
            // including every key the JDK generates. mbedtls checks the key
            // is consistent rather than assuming which was used.
            BigInteger one = BigInteger.ONE;
            BigInteger belowP = p.subtract(one);
            BigInteger belowQ = q.subtract(one);
            BigInteger smallestPeriod = belowP.divide(belowP.gcd(belowQ)).multiply(belowQ);
            if (!e.multiply(d).mod(smallestPeriod).equals(one)) {
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

    /**
     * The padding a caller asked for, named the way the JDK names it.
     *
     * <p>PKCS#1 v1.5 unless /OAEP, matching the C's default and its
     * refinement. OAEP is stated with its digest because the JDK's bare
     * "OAEPPadding" leaves the choice to a provider default, and a scheme
     * whose parameters depend on which provider answered is not one a caller
     * can interoperate with.
     */
    private static String cipherFor(boolean optimalPadding) {
        return optimalPadding
                ? "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
                : "RSA/ECB/PKCS1Padding";
    }

    byte[] enciphered(byte[] data, boolean optimalPadding) throws Exception {
        Cipher cipher = Cipher.getInstance(cipherFor(optimalPadding));
        cipher.init(Cipher.ENCRYPT_MODE, publicHalf);
        return cipher.doFinal(data);
    }

    byte[] deciphered(byte[] data, boolean optimalPadding) throws Exception {
        Cipher cipher = Cipher.getInstance(cipherFor(optimalPadding));
        cipher.init(Cipher.DECRYPT_MODE, privateHalf);
        return cipher.doFinal(data);
    }

    /**
     * The signature scheme, named for the digest and whether /PSS was asked
     * for.
     *
     * <p>SHA-256 unless the caller named another digest, which is what the C
     * falls back to when /HASH is absent.
     */
    private static String digestNamed(String asked) {
        return switch (asked) {
            case "md5" -> "MD5";
            case "sha1" -> "SHA-1";
            case "sha224" -> "SHA-224";
            case "sha384" -> "SHA-384";
            case "sha512" -> "SHA-512";
            default -> "SHA-256";
        };
    }

    /**
     * A signer or verifier, set up for the scheme the caller asked for.
     *
     * <p>PKCS#1 v1.5 is one algorithm name. PSS is not: the JDK names the
     * scheme {@code RSASSA-PSS} and takes the digest, the mask function and
     * the salt length as parameters, so they are stated rather than left to a
     * provider default. Salt as long as the digest, which is what makes a
     * signature interoperable with the mbedtls the C uses.
     */
    private static Signature signatureFor(String digest, boolean probabilistic)
            throws java.security.GeneralSecurityException {
        String named = digestNamed(digest);
        if (!probabilistic) {
            return Signature.getInstance(named.replace("-", "") + "withRSA");
        }
        Signature scheme = Signature.getInstance("RSASSA-PSS");
        scheme.setParameter(new java.security.spec.PSSParameterSpec(
                named, "MGF1",
                new java.security.spec.MGF1ParameterSpec(named),
                java.security.MessageDigest.getInstance(named).getDigestLength(),
                java.security.spec.PSSParameterSpec.TRAILER_FIELD_BC));
        return scheme;
    }

    byte[] signed(byte[] data, String digest, boolean probabilistic) throws Exception {
        Signature signing = signatureFor(digest, probabilistic);
        signing.initSign(privateHalf);
        signing.update(data);
        return signing.sign();
    }

    boolean verifies(byte[] data, byte[] signature, String digest, boolean probabilistic) {
        try {
            Signature checking = signatureFor(digest, probabilistic);
            checking.initVerify(publicHalf);
            checking.update(data);
            return checking.verify(signature);
        } catch (java.security.GeneralSecurityException | RuntimeException doesNotHold) {
            return false;
        }
    }
}
