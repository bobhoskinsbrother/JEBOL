package org.jebol.domain.eval;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Map;
import java.util.Optional;
import javax.crypto.KeyAgreement;

/**
 * A key pair on a named elliptic curve, for the exchange and for signing.
 *
 * <p>Elliptic-curve Diffie-Hellman is the same idea as the modular kind with
 * different arithmetic: instead of raising a generator to a private power in a
 * field of integers, each side multiplies a point on a curve by a private
 * number. The published value is a point and the secret is one coordinate of
 * the point both sides reach.
 *
 * <p>Everything else follows from that. A curve has to be named, a context
 * remembers which one, the published value is twice a coordinate wide plus a
 * lead byte, the secret is one coordinate wide, and two contexts on different
 * curves cannot agree on anything.
 */
final class EllipticCurveKey {

    /**
     * The curves {@code system/catalog/elliptic-curves} names, mapped to what
     * the JDK calls them.
     *
     * <p>The names in the catalogue are the SEC ones and the JDK answers to
     * those directly for the NIST and Koblitz families. The Brainpool curves
     * and the two Montgomery ones are in the catalogue and are not in the
     * JDK's default provider, so they are absent here and asking for one
     * answers none -- which is the shape the C already uses for a curve it
     * has not got.
     */
    private static final Map<String, String> CURVES_THIS_BUILD_HAS = Map.of(
            "secp192r1", "secp192r1",
            "secp224r1", "secp224r1",
            "secp256r1", "secp256r1",
            "secp384r1", "secp384r1",
            "secp521r1", "secp521r1",
            "secp256k1", "secp256k1");

    private final KeyPair pair;
    private final String curveName;
    private final ECParameterSpec parameters;
    private final int coordinateWidth;

    private EllipticCurveKey(KeyPair pair, String curveName,
            ECParameterSpec parameters, int coordinateWidth) {
        this.pair = pair;
        this.curveName = curveName;
        this.parameters = parameters;
        this.coordinateWidth = coordinateWidth;
    }

    String curveName() {
        return curveName;
    }

    int coordinateWidth() {
        return coordinateWidth;
    }

    /** A fresh key pair on a named curve, or nothing when there is no such curve. */
    static Optional<EllipticCurveKey> onCurve(String named) {
        String known = CURVES_THIS_BUILD_HAS.get(named);
        if (known == null) {
            return Optional.empty();
        }
        try {
            KeyPairGenerator generating = KeyPairGenerator.getInstance("EC");
            generating.initialize(new ECGenParameterSpec(known));
            KeyPair pair = generating.generateKeyPair();
            ECParameterSpec spec = ((java.security.interfaces.ECPublicKey)
                    pair.getPublic()).getParams();
            int width = (spec.getCurve().getField().getFieldSize() + 7) / 8;
            return Optional.of(new EllipticCurveKey(pair, named, spec, width));
        } catch (java.security.GeneralSecurityException | RuntimeException noSuchCurve) {
            return Optional.empty();
        }
    }

    /**
     * The point to send, written out in full: a lead byte saying it is
     * uncompressed, then both coordinates at the curve's width.
     */
    byte[] publishedPoint() {
        ECPoint point = ((java.security.interfaces.ECPublicKey) pair.getPublic()).getW();
        byte[] written = new byte[1 + (2 * coordinateWidth)];
        written[0] = 0x04;
        writeCoordinate(point.getAffineX(), written, 1);
        writeCoordinate(point.getAffineY(), written, 1 + coordinateWidth);
        return written;
    }

    private void writeCoordinate(BigInteger value, byte[] into, int at) {
        byte[] raw = value.toByteArray();
        int from = raw.length > coordinateWidth ? raw.length - coordinateWidth : 0;
        int taking = Math.min(raw.length, coordinateWidth);
        System.arraycopy(raw, from, into, at + coordinateWidth - taking, taking);
    }

    /**
     * The secret both sides reach, or nothing when the peer's point is not on
     * this curve.
     *
     * <p>One coordinate wide rather than two: the exchange agrees on a point
     * and only its first coordinate is used, which is why the secret is half
     * the published value's width less the lead byte.
     */
    Optional<byte[]> agreedWith(byte[] peersPoint) {
        try {
            if (peersPoint.length != 1 + (2 * coordinateWidth) || peersPoint[0] != 0x04) {
                return Optional.empty();
            }
            BigInteger x = new BigInteger(1,
                    java.util.Arrays.copyOfRange(peersPoint, 1, 1 + coordinateWidth));
            BigInteger y = new BigInteger(1, java.util.Arrays.copyOfRange(
                    peersPoint, 1 + coordinateWidth, peersPoint.length));
            KeyAgreement agreeing = KeyAgreement.getInstance("ECDH");
            agreeing.init(pair.getPrivate());
            agreeing.doPhase(KeyFactory.getInstance("EC").generatePublic(
                    new ECPublicKeySpec(new ECPoint(x, y), parameters)), true);
            return Optional.of(agreeing.generateSecret());
        } catch (java.security.GeneralSecurityException | RuntimeException cannotAgree) {
            return Optional.empty();
        }
    }

    /**
     * A signature over a hash, ASN.1 encoded.
     *
     * <p>{@code NONEwithECDSA} because the caller has already hashed: the
     * argument is named `hash` in the declaration and the C signs it as it
     * stands. Signing here draws a fresh random number each time, so two
     * signatures over one hash differ and both hold.
     */
    Optional<byte[]> signed(byte[] hash) {
        try {
            Signature signing = Signature.getInstance("NONEwithECDSA");
            signing.initSign(pair.getPrivate());
            signing.update(hash);
            return Optional.of(signing.sign());
        } catch (java.security.GeneralSecurityException | RuntimeException cannotSign) {
            return Optional.empty();
        }
    }

    /** Whether a signature holds over a hash. */
    boolean verifies(byte[] hash, byte[] signature) {
        try {
            Signature checking = Signature.getInstance("NONEwithECDSA");
            checking.initVerify(pair.getPublic());
            checking.update(hash);
            return checking.verify(signature);
        } catch (java.security.GeneralSecurityException | RuntimeException doesNotHold) {
            return false;
        }
    }

    /** The curve names a script can ask for, in the catalogue's order. */
    static java.util.List<String> curveNames() {
        return java.util.List.of("secp192r1", "secp224r1", "secp256r1",
                "secp384r1", "secp521r1", "secp192k1", "secp224k1", "secp256k1",
                "bp256r1", "bp384r1", "bp512r1", "curve25519", "curve448");
    }
}
