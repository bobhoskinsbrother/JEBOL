package org.jebol.domain.eval.hashing;

public final class UnsignedProducts {

    private UnsignedProducts() {
    }

    public static long multiplyForHighHalf(long left, long right) {
        long leftLow = left & 0xFFFF_FFFFL;
        long leftHigh = left >>> 32;
        long rightLow = right & 0xFFFF_FFFFL;
        long rightHigh = right >>> 32;
        long lowProduct = leftLow * rightLow;
        long middle = leftHigh * rightLow + (lowProduct >>> 32);
        long highOfLow = leftLow * rightHigh + (middle & 0xFFFF_FFFFL);
        return leftHigh * rightHigh + (middle >>> 32) + (highOfLow >>> 32);
    }
}
