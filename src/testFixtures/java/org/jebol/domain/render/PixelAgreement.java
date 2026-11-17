package org.jebol.domain.render;

public record PixelAgreement(int insideAShape, int onAnEdge, int compared) {

    public static final int EXACTLY = 0;

    public static final int CHANNEL_ALLOWANCE_ON_AN_EDGE = 24;

    public static final double MOST_EDGE_PIXELS_THAT_MAY_DIFFER = 0.025;

    private static final int[] CHANNEL_SHIFTS = {16, 8, 0};

    public static PixelAgreement between(ArgbPicture one, ArgbPicture other, int insideAShapeAllowance) {
        if (one.width() != other.width() || one.height() != other.height()) {
            int every = Math.max(one.width() * one.height(), other.width() * other.height());
            return new PixelAgreement(every, every, every);
        }
        int insideAShape = 0;
        int onAnEdge = 0;
        for (int down = 0; down < one.height(); down++) {
            for (int across = 0; across < one.width(); across++) {
                if (isSameColour(one, other, across, down, insideAShapeAllowance)) {
                    continue;
                }
                if (isInsideAShape(one, across, down) && isInsideAShape(other, across, down)) {
                    insideAShape++;
                } else if (!isSameColour(one, other, across, down, CHANNEL_ALLOWANCE_ON_AN_EDGE)) {
                    onAnEdge++;
                }
            }
        }
        return new PixelAgreement(insideAShape, onAnEdge, one.width() * one.height());
    }

    public double edgeShare() {
        return compared == 0 ? 0 : (double) onAnEdge / compared;
    }

    public boolean holds() {
        return insideAShape == 0 && edgeShare() <= MOST_EDGE_PIXELS_THAT_MAY_DIFFER;
    }

    private static boolean isSameColour(ArgbPicture one, ArgbPicture other, int across, int down, int allowance) {
        int here = one.rgbAt(across, down);
        int there = other.rgbAt(across, down);
        for (int shift : CHANNEL_SHIFTS) {
            if (Math.abs((here >> shift & 0xFF) - (there >> shift & 0xFF)) > allowance) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInsideAShape(ArgbPicture picture, int across, int down) {
        int middle = picture.rgbAt(across, down);
        for (int downBy = -1; downBy <= 1; downBy++) {
            for (int acrossBy = -1; acrossBy <= 1; acrossBy++) {
                int neighbourAcross = across + acrossBy;
                int neighbourDown = down + downBy;
                if (neighbourAcross < 0 || neighbourDown < 0
                        || neighbourAcross >= picture.width() || neighbourDown >= picture.height()) {
                    continue;
                }
                if (picture.rgbAt(neighbourAcross, neighbourDown) != middle) {
                    return false;
                }
            }
        }
        return true;
    }
}
