package org.jebol.domain.eval.crypto;

import javax.crypto.KeyAgreement;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
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

public final class EllipticCurveKey implements ReleasableKey {

    private boolean handedBack;

    @Override
    public void release() {
        handedBack = true;
    }

    @Override
    public boolean released() {
        return handedBack;
    }

    private enum HowPointIsPublished {

        BOTH_COORDINATES_AFTER_LEAD_BYTE,

        ONE_COORDINATE_LITTLE_ENDIAN_ON_ITS_OWN
    }

    private static final Map<String, String> CURVES_THIS_BUILD_HAS = Map.of(
            "secp256r1", "secp256r1",
            "secp384r1", "secp384r1",
            "secp521r1", "secp521r1",
            "secp256k1", "secp256k1",
            "curve25519", NamedParameterSpec.X25519.getName(),
            "curve448", NamedParameterSpec.X448.getName());

    private static final Map<String, Integer> EXCHANGE_CURVE_WIDTHS =
            Map.of("curve25519", 32, "curve448", 56);

    private static final SecureRandom RANDOMLY = new SecureRandom();

    private KeyPair pair;
    private String curveName;
    private int coordinateWidth;
    private HowPointIsPublished published;
    private WeierstrassCurve computedHere;
    private BigInteger privateNumber;
    private WeierstrassCurve.Point publicPoint;

    private EllipticCurveKey(KeyPair pair, String curveName,
            int coordinateWidth, HowPointIsPublished published) {
        this.pair = pair;
        this.curveName = curveName;
        this.coordinateWidth = coordinateWidth;
        this.published = published;
        this.computedHere = null;
        this.privateNumber = null;
        this.publicPoint = null;
    }

    private EllipticCurveKey(WeierstrassCurve curve, BigInteger privateNumber,
            WeierstrassCurve.Point publicPoint, String curveName) {
        this.pair = null;
        this.curveName = curveName;
        this.coordinateWidth = curve.coordinateWidth();
        this.published = HowPointIsPublished.BOTH_COORDINATES_AFTER_LEAD_BYTE;
        this.computedHere = curve;
        this.privateNumber = privateNumber;
        this.publicPoint = publicPoint;
    }

    private boolean doesArithmeticInThisBuild() {
        return computedHere != null;
    }

    public boolean startAgainOn(String wantedCurve) {
        Optional<EllipticCurveKey> fresh = onCurve(wantedCurve);
        if (fresh.isEmpty()) {
            return false;
        }
        EllipticCurveKey made = fresh.get();
        this.pair = made.pair;
        this.curveName = made.curveName;
        this.coordinateWidth = made.coordinateWidth;
        this.published = made.published;
        this.computedHere = made.computedHere;
        this.privateNumber = made.privateNumber;
        this.publicPoint = made.publicPoint;
        this.handedBack = false;
        return true;
    }

    public String curveName() {
        return curveName;
    }

    public static Optional<EllipticCurveKey> onCurve(String curveName) {
        Optional<WeierstrassCurve> carriedHere = WeierstrassCurve.findCurveNamed(curveName);
        if (carriedHere.isPresent()) {
            return Optional.of(makeOnCurveThisBuildComputes(curveName, carriedHere.get()));
        }
        String known = CURVES_THIS_BUILD_HAS.get(curveName);
        if (known == null) {
            return Optional.empty();
        }
        return EXCHANGE_CURVE_WIDTHS.containsKey(curveName)
                ? makeForExchangeAlone(curveName, known)
                : makeOnCurveWithTwoCoordinates(curveName, known);
    }

    private static EllipticCurveKey makeOnCurveThisBuildComputes(
            String curveName, WeierstrassCurve curve) {

        BigInteger privateNumber = curve.drawPrivateNumber(RANDOMLY);
        return new EllipticCurveKey(curve, privateNumber,
                curve.multiplyGenerator(privateNumber), curveName);
    }

    private static Optional<EllipticCurveKey> makeOnCurveWithTwoCoordinates(
            String curveName, String asJdkCallsIt) {

        try {
            KeyPairGenerator generating = KeyPairGenerator.getInstance("EC");
            generating.initialize(new ECGenParameterSpec(asJdkCallsIt));
            KeyPair pair = generating.generateKeyPair();
            int width = (findCurveBehind(pair).getCurve().getField().getFieldSize() + 7) / 8;
            return Optional.of(new EllipticCurveKey(pair, curveName, width,
                    HowPointIsPublished.BOTH_COORDINATES_AFTER_LEAD_BYTE));
        } catch (GeneralSecurityException | RuntimeException noSuchCurve) {
            return Optional.empty();
        }
    }

    private static Optional<EllipticCurveKey> makeForExchangeAlone(
            String curveName, String asJdkCallsIt) {

        try {
            KeyPairGenerator generating = KeyPairGenerator.getInstance("XDH");
            generating.initialize(new NamedParameterSpec(asJdkCallsIt));
            return Optional.of(new EllipticCurveKey(
                    generating.generateKeyPair(), curveName,
                    EXCHANGE_CURVE_WIDTHS.get(curveName),
                    HowPointIsPublished.ONE_COORDINATE_LITTLE_ENDIAN_ON_ITS_OWN));
        } catch (GeneralSecurityException | RuntimeException noSuchCurve) {
            return Optional.empty();
        }
    }

    private static ECParameterSpec findCurveBehind(KeyPair pair) {
        return ((ECPublicKey) pair.getPublic()).getParams();
    }

    public byte[] publishPoint() {
        if (doesArithmeticInThisBuild()) {
            return writeCoordinates(publicPoint.x(), publicPoint.y());
        }
        return published == HowPointIsPublished.ONE_COORDINATE_LITTLE_ENDIAN_ON_ITS_OWN
                ? writeLittleEndianAsRfc7748(
                        ((XECPublicKey) pair.getPublic()).getU())
                : writeBothCoordinatesAfterLeadByte();
    }

    private byte[] writeBothCoordinatesAfterLeadByte() {
        ECPoint point = ((ECPublicKey) pair.getPublic()).getW();
        return writeCoordinates(point.getAffineX(), point.getAffineY());
    }

    private byte[] writeCoordinates(BigInteger x, BigInteger y) {
        byte[] written = new byte[1 + (2 * coordinateWidth)];
        written[0] = 0x04;
        writeCoordinate(x, written, 1);
        writeCoordinate(y, written, 1 + coordinateWidth);
        return written;
    }

    private void writeCoordinate(BigInteger value, byte[] into, int at) {
        byte[] raw = value.toByteArray();
        int from = raw.length > coordinateWidth ? raw.length - coordinateWidth : 0;
        int taking = Math.min(raw.length, coordinateWidth);
        System.arraycopy(raw, from, into, at + coordinateWidth - taking, taking);
    }

    private byte[] writeLittleEndianAsRfc7748(BigInteger coordinate) {
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

    public Optional<byte[]> agreeWith(byte[] peersPoint) {
        if (handedBack) {
            return Optional.empty();
        }
        if (doesArithmeticInThisBuild()) {
            return computedHere.pointFromWire(peersPoint)
                    .map(theirs -> computedHere.writeCoordinateOnWire(
                            computedHere.multiply(theirs, privateNumber).x()));
        }
        try {
            return published == HowPointIsPublished.ONE_COORDINATE_LITTLE_ENDIAN_ON_ITS_OWN
                    ? agreeOnOneCoordinateWith(peersPoint)
                    : agreeOnBothCoordinatesWith(peersPoint);
        } catch (GeneralSecurityException | RuntimeException cannotAgree) {
            return Optional.empty();
        }
    }

    private Optional<byte[]> agreeOnOneCoordinateWith(byte[] peersPoint)
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

    private Optional<byte[]> agreeOnBothCoordinatesWith(byte[] peersPoint)
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
                new ECPublicKeySpec(new ECPoint(x, y), findCurveBehind(pair))), true);
        return Optional.of(agreeing.generateSecret());
    }

    public Optional<byte[]> sign(byte[] hash) {
        if (handedBack
                || published == HowPointIsPublished.ONE_COORDINATE_LITTLE_ENDIAN_ON_ITS_OWN) {
            return Optional.empty();
        }
        if (doesArithmeticInThisBuild()) {
            BigInteger[] pair =
                    computedHere.signOver(privateNumber, hash, RANDOMLY);
            return Optional.of(writeDerSequenceOfTwoIntegers(pair[0], pair[1]));
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

    public boolean verifies(byte[] hash, byte[] signature) {
        if (handedBack
                || published == HowPointIsPublished.ONE_COORDINATE_LITTLE_ENDIAN_ON_ITS_OWN) {
            return false;
        }
        if (doesArithmeticInThisBuild()) {
            return readTwoIntegersFrom(signature)
                    .filter(pair -> computedHere.verifiesSignature(
                            publicPoint, hash, pair[0], pair[1]))
                    .isPresent();
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

    public static boolean verifiesWithPublishedPoint(
            byte[] point, String curveName, byte[] hash, byte[] signature) {

        Optional<WeierstrassCurve> carriedHere = WeierstrassCurve.findCurveNamed(curveName);
        if (carriedHere.isPresent()) {
            return carriedHere.get().pointFromWire(point)
                    .flatMap(theirs -> readTwoIntegersFrom(signature)
                            .filter(pair -> carriedHere.get().verifiesSignature(
                                    theirs, hash, pair[0], pair[1])))
                    .isPresent();
        }
        String known = CURVES_THIS_BUILD_HAS.get(curveName);
        if (known == null || EXCHANGE_CURVE_WIDTHS.containsKey(curveName)) {
            return false;
        }
        try {
            AlgorithmParameters describing = AlgorithmParameters.getInstance("EC");
            describing.init(new ECGenParameterSpec(known));
            ECParameterSpec curve = describing.getParameterSpec(ECParameterSpec.class);
            int width = (curve.getCurve().getField().getFieldSize() + 7) / 8;
            if (point.length != 1 + 2 * width || point[0] != 0x04) {
                return false;
            }
            ECPoint theirs = new ECPoint(
                    new BigInteger(1, Arrays.copyOfRange(point, 1, 1 + width)),
                    new BigInteger(1, Arrays.copyOfRange(point, 1 + width, point.length)));
            Signature checking = Signature.getInstance("NONEwithECDSA");
            checking.initVerify(KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(theirs, curve)));
            checking.update(hash);
            return checking.verify(signature);
        } catch (GeneralSecurityException | RuntimeException doesNotHold) {
            return false;
        }
    }

    private static final int DER_SEQUENCE = 0x30;
    private static final int DER_INTEGER = 0x02;

    private static byte[] writeDerSequenceOfTwoIntegers(
            BigInteger first, BigInteger second) {

        byte[] left = derInteger(first);
        byte[] right = derInteger(second);
        int content = left.length + right.length;
        byte[] header = content < 0x80
                ? new byte[] {(byte) DER_SEQUENCE, (byte) content}
                : new byte[] {(byte) DER_SEQUENCE, (byte) 0x81, (byte) content};
        byte[] written = new byte[header.length + content];
        System.arraycopy(header, 0, written, 0, header.length);
        System.arraycopy(left, 0, written, header.length, left.length);
        System.arraycopy(right, 0, written, header.length + left.length, right.length);
        return written;
    }

    private static byte[] derInteger(BigInteger value) {
        byte[] magnitude = value.toByteArray();
        byte[] written = new byte[2 + magnitude.length];
        written[0] = (byte) DER_INTEGER;
        written[1] = (byte) magnitude.length;
        System.arraycopy(magnitude, 0, written, 2, magnitude.length);
        return written;
    }

    private static Optional<BigInteger[]> readTwoIntegersFrom(byte[] signature) {
        if (signature.length < 8 || (signature[0] & 0xFF) != DER_SEQUENCE) {
            return Optional.empty();
        }
        int firstInteger = (signature[1] & 0xFF) == 0x81 ? 3 : 2;
        int content = (signature[1] & 0xFF) == 0x81
                ? signature[2] & 0xFF
                : signature[1] & 0xFF;
        if (content != signature.length - firstInteger) {
            return Optional.empty();
        }
        Optional<BigInteger> first = readDerIntegerAt(signature, firstInteger);
        if (first.isEmpty()) {
            return Optional.empty();
        }
        int after = firstInteger + 2 + (signature[firstInteger + 1] & 0xFF);
        return readDerIntegerAt(signature, after)
                .map(second -> new BigInteger[] {first.get(), second});
    }

    private static Optional<BigInteger> readDerIntegerAt(byte[] written, int at) {
        if (at + 1 >= written.length || (written[at] & 0xFF) != DER_INTEGER) {
            return Optional.empty();
        }
        int length = written[at + 1] & 0xFF;
        if (length == 0 || at + 2 + length > written.length) {
            return Optional.empty();
        }
        return Optional.of(new BigInteger(
                Arrays.copyOfRange(written, at + 2, at + 2 + length)));
    }

    public static List<String> curveNamesInCataloguesOrder() {
        return List.of("secp192r1", "secp224r1", "secp256r1",
                "secp384r1", "secp521r1", "secp192k1", "secp224k1", "secp256k1",
                "bp256r1", "bp384r1", "bp512r1", "curve25519", "curve448");
    }
}
