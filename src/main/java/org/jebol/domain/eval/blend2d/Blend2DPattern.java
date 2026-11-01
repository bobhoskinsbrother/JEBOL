package org.jebol.domain.eval.blend2d;

public final class Blend2DPattern implements Blend2DPaint {

    private static final int PAD = 0;

    private static final int REPEAT = 1;

    private static final int REFLECT = 2;

    private static final int[][] EXTEND_PER_AXIS = {
        {PAD, PAD}, {REPEAT, REPEAT}, {REFLECT, REFLECT},
        {PAD, REPEAT}, {PAD, REFLECT}, {REPEAT, PAD}, {REPEAT, REFLECT}, {REFLECT, PAD}, {REFLECT, REPEAT}};

    private final int[] premultiplied;

    private final int wide;

    private final int high;

    private final int extendX;

    private final int extendY;

    private final boolean isOpaqueFormat;

    public Blend2DPattern(Blend2DImage image, int extendMode) {
        this.premultiplied = image.pixels().clone();
        this.wide = image.wide();
        this.high = image.high();
        int[] perAxis = EXTEND_PER_AXIS[Math.clamp(extendMode, 0, EXTEND_PER_AXIS.length - 1)];
        extendX = perAxis[0];
        extendY = perAxis[1];
        this.isOpaqueFormat = image.isOpaqueFormat();
    }

    @Override
    public boolean isOpaque() {
        return isOpaqueFormat;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public int premultipliedAt(double x, double y) {
        double sampleX = x - 0.5;
        double sampleY = y - 0.5;
        int left = (int) Math.floor(sampleX);
        int top = (int) Math.floor(sampleY);
        double weightX = sampleX - left;
        double weightY = sampleY - top;
        int topLeft = pixelAt(left, top);
        int topRight = pixelAt(left + 1, top);
        int bottomLeft = pixelAt(left, top + 1);
        int bottomRight = pixelAt(left + 1, top + 1);
        int result = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            double upper = channel(topLeft, shift) * (1 - weightX) + channel(topRight, shift) * weightX;
            double lower = channel(bottomLeft, shift) * (1 - weightX) + channel(bottomRight, shift) * weightX;
            int value = (int) Math.round(upper * (1 - weightY) + lower * weightY);
            result |= Math.clamp(value, 0, 255) << shift;
        }
        return result;
    }

    private static int channel(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    private int pixelAt(int across, int down) {
        return premultiplied[extend(down, high, extendY) * wide + extend(across, wide, extendX)];
    }

    private static int extend(int at, int length, int mode) {
        return switch (mode) {
            case REPEAT -> Math.floorMod(at, length);
            case REFLECT -> {
                int folded = Math.floorMod(at, 2 * length);
                yield folded < length ? folded : 2 * length - 1 - folded;
            }
            default -> Math.clamp(at, 0, length - 1);
        };
    }
}
