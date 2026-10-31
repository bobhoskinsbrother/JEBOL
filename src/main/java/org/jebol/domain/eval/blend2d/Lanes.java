package org.jebol.domain.eval.blend2d;

final class Lanes {

    private static final int LANE_BITS = 16;

    private static final int LANE_MASK = 0xFFFF;

    private static final int LANE_COUNT = 4;

    private static final int ALPHA = 3;

    private static final int BYTE_MASK = 0xFF;

    private Lanes() {
    }

    static int laneAt(long lanes, int index) {
        return (int) (lanes >>> (LANE_BITS * index)) & LANE_MASK;
    }

    static long withLane(long lanes, int index, int value) {
        int shift = LANE_BITS * index;
        return (lanes & ~((long) LANE_MASK << shift)) | ((long) (value & LANE_MASK) << shift);
    }

    static long of(int blue, int green, int red, int alpha) {
        return (blue & LANE_MASK)
                | (long) (green & LANE_MASK) << LANE_BITS
                | (long) (red & LANE_MASK) << (2 * LANE_BITS)
                | (long) (alpha & LANE_MASK) << (3 * LANE_BITS);
    }

    static long broadcast(int value) {
        return of(value, value, value, value);
    }

    static long unpack(int pixel) {
        return of(pixel & BYTE_MASK, (pixel >>> 8) & BYTE_MASK, (pixel >>> 16) & BYTE_MASK, pixel >>> 24);
    }

    static int pack(long lanes) {
        int pixel = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            pixel |= saturateSignedToByte(laneAt(lanes, index)) << (8 * index);
        }
        return pixel;
    }

    static int saturateSignedToByte(int lane) {
        int signed = (short) lane;
        return Math.clamp(signed, 0, BYTE_MASK);
    }

    static long multiply(long left, long right) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            result = withLane(result, index, laneAt(left, index) * laneAt(right, index));
        }
        return result;
    }

    static long add(long left, long right) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            result = withLane(result, index, laneAt(left, index) + laneAt(right, index));
        }
        return result;
    }

    static long subtract(long left, long right) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            result = withLane(result, index, laneAt(left, index) - laneAt(right, index));
        }
        return result;
    }

    static long subtractSaturated(long left, long right) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            result = withLane(result, index, Math.max(0, laneAt(left, index) - laneAt(right, index)));
        }
        return result;
    }

    static long divideBy255(long lanes) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            int rounded = (laneAt(lanes, index) + 0x80) & LANE_MASK;
            result = withLane(result, index, (rounded * 257) >>> LANE_BITS);
        }
        return result;
    }

    static long multiplyBy257High(long lanes) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            result = withLane(result, index, (laneAt(lanes, index) * 257) >>> LANE_BITS);
        }
        return result;
    }

    static long invert255(long lanes) {
        return lanes ^ broadcast(BYTE_MASK);
    }

    static long expandAlpha(long lanes) {
        return broadcast(laneAt(lanes, ALPHA));
    }

    static long zeroAlpha(long lanes) {
        return withLane(lanes, ALPHA, 0);
    }

    static long fillAlpha255(long lanes) {
        return withLane(lanes, ALPHA, laneAt(lanes, ALPHA) | BYTE_MASK);
    }

    static long shiftLeftOne(long lanes) {
        return shiftLeft(lanes, 1);
    }

    static long shiftLeft(long lanes, int bits) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            result = withLane(result, index, laneAt(lanes, index) << bits);
        }
        return result;
    }

    static long compareGreaterSigned(long left, long right) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            boolean greater = (short) laneAt(left, index) > (short) laneAt(right, index);
            result = withLane(result, index, greater ? LANE_MASK : 0);
        }
        return result;
    }

    static long minimumSigned(long left, long right) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            result = withLane(result, index, Math.min((short) laneAt(left, index), (short) laneAt(right, index)));
        }
        return result;
    }

    static long minimumUnsigned(long left, long right) {
        long result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            result = withLane(result, index, Math.min(laneAt(left, index), laneAt(right, index)));
        }
        return result;
    }

    static long minimumBytes(long left, long right) {
        long result = 0;
        for (int index = 0; index < 2 * LANE_COUNT; index++) {
            int shift = 8 * index;
            long smaller = Math.min((left >>> shift) & BYTE_MASK, (right >>> shift) & BYTE_MASK);
            result |= smaller << shift;
        }
        return result;
    }

    static long maximumBytes(long left, long right) {
        long result = 0;
        for (int index = 0; index < 2 * LANE_COUNT; index++) {
            int shift = 8 * index;
            long larger = Math.max((left >>> shift) & BYTE_MASK, (right >>> shift) & BYTE_MASK);
            result |= larger << shift;
        }
        return result;
    }

    static int addBytesSaturated(int left, int right) {
        int result = 0;
        for (int index = 0; index < LANE_COUNT; index++) {
            int shift = 8 * index;
            int sum = ((left >>> shift) & BYTE_MASK) + ((right >>> shift) & BYTE_MASK);
            result |= Math.min(BYTE_MASK, sum) << shift;
        }
        return result;
    }

    static long andNot(long kept, long cleared) {
        return kept & ~cleared;
    }
}
