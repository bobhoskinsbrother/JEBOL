package org.jebol.domain.eval;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class WeierstrassCurve {

    record Point(BigInteger x, BigInteger y) {

        static final Point INFINITY = new Point(null, null);

        boolean isInfinity() {
            return x == null;
        }
    }

    private final BigInteger prime;
    private final BigInteger coefficientA;
    private final BigInteger coefficientB;
    private final Point generator;
    private final BigInteger order;
    private final int coordinateWidth;

    private WeierstrassCurve(String prime, String coefficientA, String coefficientB,
            String generatorX, String generatorY, String order) {

        this.prime = new BigInteger(prime, 16);
        this.coefficientA = new BigInteger(coefficientA, 16);
        this.coefficientB = new BigInteger(coefficientB, 16);
        this.generator = new Point(
                new BigInteger(generatorX, 16), new BigInteger(generatorY, 16));
        this.order = new BigInteger(order, 16);
        this.coordinateWidth = (this.prime.bitLength() + 7) / 8;
    }

    int coordinateWidth() {
        return coordinateWidth;
    }

    BigInteger order() {
        return order;
    }

    boolean holds(Point point) {
        if (point.isInfinity()) {
            return true;
        }
        BigInteger left = point.y().multiply(point.y()).mod(prime);
        BigInteger right = point.x().multiply(point.x()).add(coefficientA)
                .multiply(point.x()).add(coefficientB).mod(prime);
        return left.equals(right);
    }

    boolean itsGeneratorIsOnItAndHasTheStatedOrder() {
        return holds(generator) && multiply(generator, order).isInfinity();
    }

    Point doubled(Point point) {
        if (point.isInfinity() || point.y().signum() == 0) {
            return Point.INFINITY;
        }
        BigInteger slope = point.x().multiply(point.x()).multiply(BigInteger.valueOf(3))
                .add(coefficientA)
                .multiply(point.y().add(point.y()).modInverse(prime))
                .mod(prime);
        return fromSlope(slope, point.x(), point.x(), point.y());
    }

    Point added(Point left, Point right) {
        if (left.isInfinity()) {
            return right;
        }
        if (right.isInfinity()) {
            return left;
        }
        if (left.x().equals(right.x())) {
            return left.y().equals(right.y()) ? doubled(left) : Point.INFINITY;
        }
        BigInteger slope = right.y().subtract(left.y())
                .multiply(right.x().subtract(left.x()).modInverse(prime))
                .mod(prime);
        return fromSlope(slope, left.x(), right.x(), left.y());
    }

    private Point fromSlope(BigInteger slope,
            BigInteger leftX, BigInteger rightX, BigInteger leftY) {

        BigInteger x = slope.multiply(slope).subtract(leftX).subtract(rightX).mod(prime);
        return new Point(x, slope.multiply(leftX.subtract(x)).subtract(leftY).mod(prime));
    }

    Point multiply(Point point, BigInteger by) {
        Point running = Point.INFINITY;
        for (int bit = by.bitLength() - 1; bit >= 0; bit--) {
            running = doubled(running);
            if (by.testBit(bit)) {
                running = added(running, point);
            }
        }
        return running;
    }

    Point timesTheGenerator(BigInteger by) {
        return multiply(generator, by);
    }

    BigInteger aPrivateNumber(SecureRandom randomly) {
        BigInteger chosen;
        do {
            chosen = new BigInteger(order.bitLength(), randomly);
        } while (chosen.signum() == 0 || chosen.compareTo(order) >= 0);
        return chosen;
    }

    BigInteger[] signatureOver(
            BigInteger privateNumber, byte[] hash, SecureRandom randomly) {

        BigInteger digest = theLeftmostBitsOf(hash);
        while (true) {
            BigInteger chosen = aPrivateNumber(randomly);
            Point marker = timesTheGenerator(chosen);
            BigInteger first = marker.x().mod(order);
            if (first.signum() == 0) {
                continue;
            }
            BigInteger second = chosen.modInverse(order)
                    .multiply(digest.add(first.multiply(privateNumber)))
                    .mod(order);
            if (second.signum() != 0) {
                return new BigInteger[] {first, second};
            }
        }
    }

    boolean signatureHolds(
            Point publicPoint, byte[] hash, BigInteger first, BigInteger second) {

        if (first.signum() <= 0 || second.signum() <= 0
                || first.compareTo(order) >= 0 || second.compareTo(order) >= 0) {
            return false;
        }
        BigInteger digest = theLeftmostBitsOf(hash);
        BigInteger inverse = second.modInverse(order);
        Point marker = added(
                timesTheGenerator(digest.multiply(inverse).mod(order)),
                multiply(publicPoint, first.multiply(inverse).mod(order)));
        return !marker.isInfinity() && marker.x().mod(order).equals(first);
    }

    private BigInteger theLeftmostBitsOf(byte[] hash) {
        BigInteger whole = new BigInteger(1, hash);
        int beyondTheOrder = hash.length * 8 - order.bitLength();
        return beyondTheOrder > 0 ? whole.shiftRight(beyondTheOrder) : whole;
    }

    Optional<Point> pointFromTheWire(byte[] written) {
        if (written.length != 1 + 2 * coordinateWidth || written[0] != 0x04) {
            return Optional.empty();
        }
        Point read = new Point(
                new BigInteger(1, java.util.Arrays.copyOfRange(
                        written, 1, 1 + coordinateWidth)),
                new BigInteger(1, java.util.Arrays.copyOfRange(
                        written, 1 + coordinateWidth, written.length)));
        return holds(read) ? Optional.of(read) : Optional.empty();
    }

    byte[] coordinateOnTheWire(BigInteger coordinate) {
        byte[] written = new byte[coordinateWidth];
        byte[] raw = coordinate.toByteArray();
        int taking = Math.min(raw.length, coordinateWidth);
        System.arraycopy(raw, raw.length - taking, written,
                coordinateWidth - taking, taking);
        return written;
    }

    private static final Map<String, WeierstrassCurve> THE_ONES_THE_JDK_DROPPED =
            theOnesTheJdkDropped();

    static Optional<WeierstrassCurve> named(String curve) {
        return Optional.ofNullable(THE_ONES_THE_JDK_DROPPED.get(curve));
    }

    private static Map<String, WeierstrassCurve> theOnesTheJdkDropped() {
        Map<String, WeierstrassCurve> carried = new LinkedHashMap<>();
        carried.put("secp192r1", new WeierstrassCurve(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFF",
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFC",
                "64210519E59C80E70FA7E9AB72243049FEB8DEECC146B9B1",
                "188DA80EB03090F67CBF20EB43A18800F4FF0AFD82FF1012",
                "07192B95FFC8DA78631011ED6B24CDD573F977A11E794811",
                "FFFFFFFFFFFFFFFFFFFFFFFF99DEF836146BC9B1B4D22831"));
        carried.put("secp224r1", new WeierstrassCurve(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF000000000000000000000001",
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFE",
                "B4050A850C04B3ABF54132565044B0B7D7BFD8BA270B39432355FFB4",
                "B70E0CBD6BB4BF7F321390B94A03C1D356C21122343280D6115C1D21",
                "BD376388B5F723FB4C22DFE6CD4375A05A07476444D5819985007E34",
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFF16A2E0B8F03E13DD29455C5C2A3D"));
        carried.put("secp192k1", new WeierstrassCurve(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFEE37",
                "0",
                "3",
                "DB4FF10EC057E9AE26B07D0280B7F4341DA5D1B1EAE06C7D",
                "9B2F2F6D9C5628A7844163D015BE86344082AA88D95E2F9D",
                "FFFFFFFFFFFFFFFFFFFFFFFE26F2FC170F69466A74DEFD8D"));
        carried.put("secp224k1", new WeierstrassCurve(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFE56D",
                "0",
                "5",
                "A1455B334DF099DF30FC28A169A467E9E47075A90F7E650EB6B7A45C",
                "7E089FED7FBA344282CAFBD6F7E319F7C0B0BD59E2CA4BDB556D61A5",
                "010000000000000000000000000001DCE8D2EC6184CAF0A971769FB1F7"));
        carried.put("secp256k1", new WeierstrassCurve(
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F",
                "0",
                "7",
                "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798",
                "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8",
                "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141"));
        carried.put("bp256r1", new WeierstrassCurve(
                "A9FB57DBA1EEA9BC3E660A909D838D726E3BF623D52620282013481D1F6E5377",
                "7D5A0975FC2C3057EEF67530417AFFE7FB8055C126DC5C6CE94A4B44F330B5D9",
                "26DC5C6CE94A4B44F330B5D9BBD77CBF958416295CF7E1CE6BCCDC18FF8C07B6",
                "8BD2AEB9CB7E57CB2C4B482FFC81B7AFB9DE27E1E3BD23C23A4453BD9ACE3262",
                "547EF835C3DAC4FD97F8461A14611DC9C27745132DED8E545C1D54C72F046997",
                "A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7"));
        carried.put("bp384r1", new WeierstrassCurve(
                "8CB91E82A3386D280F5D6F7E50E641DF152F7109ED5456B412B1DA197FB71123"
                        + "ACD3A729901D1A71874700133107EC53",
                "7BC382C63D8C150C3C72080ACE05AFA0C2BEA28E4FB22787139165EFBA91F90F"
                        + "8AA5814A503AD4EB04A8C7DD22CE2826",
                "04A8C7DD22CE28268B39B55416F0447C2FB77DE107DCD2A62E880EA53EEB62D5"
                        + "7CB4390295DBC9943AB78696FA504C11",
                "1D1C64F068CF45FFA2A63A81B7C13F6B8847A3E77EF14FE3DB7FCAFE0CBD10E8"
                        + "E826E03436D646AAEF87B2E247D4AF1E",
                "8ABE1D7520F9C2A45CB1EB8E95CFD55262B70B29FEEC5864E19C054FF9912928"
                        + "0E4646217791811142820341263C5315",
                "8CB91E82A3386D280F5D6F7E50E641DF152F7109ED5456B31F166E6CAC0425A7"
                        + "CF3AB6AF6B7FC3103B883202E9046565"));
        carried.put("bp512r1", new WeierstrassCurve(
                "AADD9DB8DBE9C48B3FD4E6AE33C9FC07CB308DB3B3C9D20ED6639CCA70330871"
                        + "7D4D9B009BC66842AECDA12AE6A380E62881FF2F2D82C68528AA6056583A48F3",
                "7830A3318B603B89E2327145AC234CC594CBDD8D3DF91610A83441CAEA9863BC"
                        + "2DED5D5AA8253AA10A2EF1C98B9AC8B57F1117A72BF2C7B9E7C1AC4D77FC94CA",
                "3DF91610A83441CAEA9863BC2DED5D5AA8253AA10A2EF1C98B9AC8B57F1117A7"
                        + "2BF2C7B9E7C1AC4D77FC94CADC083E67984050B75EBAE5DD2809BD638016F723",
                "81AEE4BDD82ED9645A21322E9C4C6A9385ED9F70B5D916C1B43B62EEF4D0098E"
                        + "FF3B1F78E2D0D48D50D1687B93B97D5F7C6D5047406A5E688B352209BCB9F822",
                "7DDE385D566332ECC0EABFA9CF7822FDF209F70024A57B1AA000C55B881F8111"
                        + "B2DCDE494A5F485E5BCA4BD88A2763AED1CA2B2FA8F0540678CD1E0F3AD80892",
                "AADD9DB8DBE9C48B3FD4E6AE33C9FC07CB308DB3B3C9D20ED6639CCA70330870"
                        + "553E5C414CA92619418661197FAC10471DB1D381085DDADDB58796829CA90069"));
        return Map.copyOf(carried);
    }

    static java.util.Set<String> namesCarriedHere() {
        return THE_ONES_THE_JDK_DROPPED.keySet();
    }
}
