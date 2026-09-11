package org.jebol.domain.eval;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.NamedParameterSpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * remembers which one, the secret is one coordinate wide, and two contexts on
 * different curves cannot agree on anything.
 *
 * <p>Two families, because two of the curves are built for the exchange and
 * for nothing else. Their arithmetic never needs a point's second coordinate,
 * so they publish the first on its own and cannot sign at all -- see
 * {@link HowThePointIsPublished}.
 */
final class EllipticCurveKey {

    /**
     * Which of the two shapes a curve's published value takes.
     *
     * <p>Not a detail of the encoding. Rebol's own TLS asks for curve25519
     * first -- the scheme's {@code supported-groups} begins with it and the
     * client hello does {@code curve: first supported-groups} -- so a build
     * that serves only the older family cannot write a hello at all, and
     * {@code read https://} stops before a byte leaves.
     */
    private enum HowThePointIsPublished {

        /** A lead byte saying the point is uncompressed, then both coordinates. */
        BOTH_COORDINATES_AFTER_A_LEAD_BYTE,

        /** One coordinate, little-endian, with nothing in front of it. */
        ONE_COORDINATE_ON_ITS_OWN
    }

    /**
     * The curves this build serves, mapped to what the JDK calls them.
     *
     * <p>The catalogue names all thirteen a real Rebol lists, and the JDK's
     * default provider has fewer: the Brainpool family is absent, and the
     * narrower NIST and Koblitz curves were withdrawn from it. Asking for one
     * of those answers none, which is the shape the C already uses for a curve
     * a build has not got.
     */
    private static final Map<String, String> CURVES_THIS_BUILD_HAS = Map.of(
            "secp256r1", "secp256r1",
            "secp384r1", "secp384r1",
            "secp521r1", "secp521r1",
            "secp256k1", "secp256k1",
            "curve25519", NamedParameterSpec.X25519.getName(),
            "curve448", NamedParameterSpec.X448.getName());

    private static final Map<String, Integer> WIDTH_OF_THE_EXCHANGE_ONLY_CURVES =
            Map.of("curve25519", 32, "curve448", 56);

    private final KeyPair pair;
    private final String curveName;
    private final int coordinateWidth;
    private final HowThePointIsPublished published;

    private EllipticCurveKey(KeyPair pair, String curveName,
            int coordinateWidth, HowThePointIsPublished published) {
        this.pair = pair;
        this.curveName = curveName;
        this.coordinateWidth = coordinateWidth;
        this.published = published;
    }

    String curveName() {
        return curveName;
    }

    /** A fresh key pair on a named curve, or nothing when there is no such curve. */
    static Optional<EllipticCurveKey> onCurve(String named) {
        String known = CURVES_THIS_BUILD_HAS.get(named);
        if (known == null) {
            return Optional.empty();
        }
        return WIDTH_OF_THE_EXCHANGE_ONLY_CURVES.containsKey(named)
                ? madeForTheExchangeAlone(named, known)
                : madeOnACurveWithTwoCoordinates(named, known);
    }

    private static Optional<EllipticCurveKey> madeOnACurveWithTwoCoordinates(
            String named, String asTheJdkCallsIt) {

        try {
            KeyPairGenerator generating = KeyPairGenerator.getInstance("EC");
            generating.initialize(new ECGenParameterSpec(asTheJdkCallsIt));
            KeyPair pair = generating.generateKeyPair();
            int width = (theCurveBehind(pair).getCurve().getField().getFieldSize() + 7) / 8;
            return Optional.of(new EllipticCurveKey(pair, named, width,
                    HowThePointIsPublished.BOTH_COORDINATES_AFTER_A_LEAD_BYTE));
        } catch (GeneralSecurityException | RuntimeException noSuchCurve) {
            return Optional.empty();
        }
    }

    private static Optional<EllipticCurveKey> madeForTheExchangeAlone(
            String named, String asTheJdkCallsIt) {

        try {
            KeyPairGenerator generating = KeyPairGenerator.getInstance("XDH");
            generating.initialize(new NamedParameterSpec(asTheJdkCallsIt));
            return Optional.of(new EllipticCurveKey(
                    generating.generateKeyPair(), named,
                    WIDTH_OF_THE_EXCHANGE_ONLY_CURVES.get(named),
                    HowThePointIsPublished.ONE_COORDINATE_ON_ITS_OWN));
        } catch (GeneralSecurityException | RuntimeException noSuchCurve) {
            return Optional.empty();
        }
    }

    private static ECParameterSpec theCurveBehind(KeyPair pair) {
        return ((ECPublicKey) pair.getPublic()).getParams();
    }

    /**
     * The value to send: both coordinates behind a lead byte, or the first on
     * its own where that is all the curve's arithmetic uses.
     */
    byte[] publishedPoint() {
        return published == HowThePointIsPublished.ONE_COORDINATE_ON_ITS_OWN
                ? littleEndian(((XECPublicKey) pair.getPublic()).getU())
                : bothCoordinatesAfterALeadByte();
    }

    private byte[] bothCoordinatesAfterALeadByte() {
        ECPoint point = ((ECPublicKey) pair.getPublic()).getW();
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
     * A coordinate written the way RFC 7748 writes one: least significant byte
     * first, padded to the curve's width.
     *
     * <p>The JDK hands it over as a plain number and the wire wants bytes in
     * the other order, so the two disagree on a value neither of them is
     * wrong about.
     */
    private byte[] littleEndian(BigInteger coordinate) {
        byte[] written = new byte[coordinateWidth];
        byte[] biggestFirst = coordinate.toByteArray();
        int taking = Math.min(biggestFirst.length, coordinateWidth);
        for (int step = 0; step < taking; step++) {
            written[step] = biggestFirst[biggestFirst.length - 1 - step];
        }
        return written;
    }

    private static BigInteger fromLittleEndian(byte[] written) {
        byte[] biggestFirst = new byte[written.length];
        for (int step = 0; step < written.length; step++) {
            biggestFirst[step] = written[written.length - 1 - step];
        }
        return new BigInteger(1, biggestFirst);
    }

    /**
     * The secret both sides reach, or nothing when the peer's value is not one
     * this curve can use.
     *
     * <p>One coordinate wide either way: the exchange agrees on a point and
     * only its first coordinate is used, which is why the secret is half the
     * published value's width less the lead byte on the curves that send both.
     */
    Optional<byte[]> agreedWith(byte[] peersPoint) {
        try {
            return published == HowThePointIsPublished.ONE_COORDINATE_ON_ITS_OWN
                    ? agreedOnOneCoordinateWith(peersPoint)
                    : agreedOnBothCoordinatesWith(peersPoint);
        } catch (GeneralSecurityException | RuntimeException cannotAgree) {
            return Optional.empty();
        }
    }

    private Optional<byte[]> agreedOnOneCoordinateWith(byte[] peersPoint)
            throws GeneralSecurityException {

        if (peersPoint.length != coordinateWidth) {
            return Optional.empty();
        }
        NamedParameterSpec curve =
                new NamedParameterSpec(CURVES_THIS_BUILD_HAS.get(curveName));
        KeyAgreement agreeing = KeyAgreement.getInstance("XDH");
        agreeing.init(pair.getPrivate());
        agreeing.doPhase(KeyFactory.getInstance("XDH").generatePublic(
                new XECPublicKeySpec(curve, fromLittleEndian(peersPoint))), true);
        return Optional.of(agreeing.generateSecret());
    }

    private Optional<byte[]> agreedOnBothCoordinatesWith(byte[] peersPoint)
            throws GeneralSecurityException {

        if (peersPoint.length != 1 + (2 * coordinateWidth) || peersPoint[0] != 0x04) {
            return Optional.empty();
        }
        BigInteger x = new BigInteger(1,
                Arrays.copyOfRange(peersPoint, 1, 1 + coordinateWidth));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(
                peersPoint, 1 + coordinateWidth, peersPoint.length));
        KeyAgreement agreeing = KeyAgreement.getInstance("ECDH");
        agreeing.init(pair.getPrivate());
        agreeing.doPhase(KeyFactory.getInstance("EC").generatePublic(
                new ECPublicKeySpec(new ECPoint(x, y), theCurveBehind(pair))), true);
        return Optional.of(agreeing.generateSecret());
    }

    /**
     * A signature over a hash, ASN.1 encoded.
     *
     * <p>{@code NONEwithECDSA} because the caller has already hashed: the
     * argument is named `hash` in the declaration and the C signs it as it
     * stands. Signing here draws a fresh random number each time, so two
     * signatures over one hash differ and both hold.
     *
     * <p>A curve made for the exchange alone signs nothing, because a key on
     * one is a number to multiply a point by and not a signing key.
     */
    Optional<byte[]> signed(byte[] hash) {
        if (published == HowThePointIsPublished.ONE_COORDINATE_ON_ITS_OWN) {
            return Optional.empty();
        }
        try {
            Signature signing = Signature.getInstance("NONEwithECDSA");
            signing.initSign(pair.getPrivate());
            signing.update(hash);
            return Optional.of(signing.sign());
        } catch (GeneralSecurityException | RuntimeException cannotSign) {
            return Optional.empty();
        }
    }

    /** Whether a signature holds over a hash. */
    boolean verifies(byte[] hash, byte[] signature) {
        if (published == HowThePointIsPublished.ONE_COORDINATE_ON_ITS_OWN) {
            return false;
        }
        try {
            Signature checking = Signature.getInstance("NONEwithECDSA");
            checking.initVerify(pair.getPublic());
            checking.update(hash);
            return checking.verify(signature);
        } catch (GeneralSecurityException | RuntimeException doesNotHold) {
            return false;
        }
    }

    /** The curve names a script can ask for, in the catalogue's order. */
    static List<String> curveNames() {
        return List.of("secp192r1", "secp224r1", "secp256r1",
                "secp384r1", "secp521r1", "secp192k1", "secp224k1", "secp256k1",
                "bp256r1", "bp384r1", "bp512r1", "curve25519", "curve448");
    }
}
