package org.jebol.domain.eval.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Optional;

public final class MontgomeryCurve {

    private static final BigInteger TWO = BigInteger.TWO;

    private static final MontgomeryCurve CURVE25519 = new MontgomeryCurve(
            TWO.pow(255).subtract(BigInteger.valueOf(19)), 121665, 255, 32, 9);

    private static final MontgomeryCurve CURVE448 = new MontgomeryCurve(
            TWO.pow(448).subtract(TWO.pow(224)).subtract(BigInteger.ONE), 39081, 448, 56, 5);

    private final BigInteger prime;
    private final BigInteger a24;
    private final int bits;
    private final int width;
    private final byte[] basePoint;

    private MontgomeryCurve(BigInteger prime, int a24, int bits, int width, int baseU) {
        this.prime = prime;
        this.a24 = BigInteger.valueOf(a24);
        this.bits = bits;
        this.width = width;
        this.basePoint = new byte[width];
        this.basePoint[0] = (byte) baseU;
    }

    public static Optional<MontgomeryCurve> named(String curveName) {
        return switch (curveName) {
            case "curve25519" -> Optional.of(CURVE25519);
            case "curve448" -> Optional.of(CURVE448);
            default -> Optional.empty();
        };
    }

    public int width() {
        return width;
    }

    public byte[] drawScalar(SecureRandom random) {
        byte[] scalar = new byte[width];
        random.nextBytes(scalar);
        return scalar;
    }

    public byte[] publicFor(byte[] scalar) {
        return ladder(scalar, basePoint);
    }

    public Optional<byte[]> agree(byte[] scalar, byte[] peersPoint) {
        if (peersPoint.length != width) {
            return Optional.empty();
        }
        byte[] shared = ladder(scalar, peersPoint);
        for (byte octet : shared) {
            if (octet != 0) {
                return Optional.of(shared);
            }
        }
        return Optional.empty();
    }

    private byte[] ladder(byte[] scalar, byte[] point) {
        BigInteger k = clamped(scalar);
        BigInteger x1 = decodedU(point);
        BigInteger x2 = BigInteger.ONE;
        BigInteger z2 = BigInteger.ZERO;
        BigInteger x3 = x1;
        BigInteger z3 = BigInteger.ONE;
        boolean swapped = false;
        for (int bit = bits - 1; bit >= 0; bit--) {
            boolean set = k.testBit(bit);
            if (swapped != set) {
                BigInteger heldX = x2;
                x2 = x3;
                x3 = heldX;
                BigInteger heldZ = z2;
                z2 = z3;
                z3 = heldZ;
            }
            swapped = set;
            BigInteger a = x2.add(z2).mod(prime);
            BigInteger aa = a.multiply(a).mod(prime);
            BigInteger b = x2.subtract(z2).mod(prime);
            BigInteger bb = b.multiply(b).mod(prime);
            BigInteger e = aa.subtract(bb).mod(prime);
            BigInteger c = x3.add(z3).mod(prime);
            BigInteger d = x3.subtract(z3).mod(prime);
            BigInteger da = d.multiply(a).mod(prime);
            BigInteger cb = c.multiply(b).mod(prime);
            BigInteger sum = da.add(cb);
            BigInteger difference = da.subtract(cb);
            x3 = sum.multiply(sum).mod(prime);
            z3 = x1.multiply(difference.multiply(difference)).mod(prime);
            x2 = aa.multiply(bb).mod(prime);
            z2 = e.multiply(aa.add(a24.multiply(e))).mod(prime);
        }
        if (swapped) {
            x2 = x3;
            z2 = z3;
        }
        BigInteger u = x2.multiply(z2.modPow(prime.subtract(TWO), prime)).mod(prime);
        return littleEndian(u);
    }

    private BigInteger clamped(byte[] scalar) {
        byte[] k = new byte[width];
        System.arraycopy(scalar, 0, k, 0, Math.min(width, scalar.length));
        if (width == 32) {
            k[0] &= (byte) 248;
            k[31] &= 127;
            k[31] |= 64;
        } else {
            k[0] &= (byte) 252;
            k[55] |= (byte) 128;
        }
        return fromLittleEndian(k);
    }

    private BigInteger decodedU(byte[] point) {
        byte[] u = point.clone();
        if (width == 32) {
            u[31] &= 127;
        }
        return fromLittleEndian(u).mod(prime);
    }

    private byte[] littleEndian(BigInteger value) {
        byte[] written = new byte[width];
        byte[] biggestFirst = value.toByteArray();
        int taking = Math.min(width, biggestFirst.length);
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
}
