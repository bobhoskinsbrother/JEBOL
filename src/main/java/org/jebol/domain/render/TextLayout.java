package org.jebol.domain.render;

public record TextLayout(
        int originAcross, int originDown,
        int marginAcross, int marginDown,
        TextAlignment align, TextVerticalAlignment valign,
        int shadowAcross, int shadowDown,
        boolean wraps) {

    private static final int THE_STANDARD_INSET = 2;
    private static final boolean THE_STANDARD_PARA_WRAPS = true;

    public static final TextLayout STANDARD = new TextLayout(
            THE_STANDARD_INSET, THE_STANDARD_INSET, THE_STANDARD_INSET, THE_STANDARD_INSET,
            TextAlignment.LEFT, TextVerticalAlignment.TOP, 0, 0);

    public TextLayout(int originAcross, int originDown, int marginAcross, int marginDown,
            TextAlignment align, TextVerticalAlignment valign, int shadowAcross, int shadowDown) {
        this(originAcross, originDown, marginAcross, marginDown, align, valign, shadowAcross, shadowDown,
                THE_STANDARD_PARA_WRAPS);
    }

    public record LinePlacement(double across, double baseline) {
    }

    public boolean castsAShadow() {
        return shadowAcross != 0 || shadowDown != 0;
    }

    public LinePlacement whereTheLineGoes(Placement box, double lineWide, double ascent, double descent) {
        return new LinePlacement(
                whereALineStarts(box, lineWide),
                whereTheStackStarts(box, ascent + descent) + ascent);
    }

    public double whereALineStarts(Placement box, double lineWide) {
        double roomAcross = box.wide() - originAcross - marginAcross;
        return box.across() + originAcross + theShareOfTheSpareRoomBefore(roomAcross - lineWide);
    }

    public double whereTheStackStarts(Placement box, double stackHigh) {
        double roomDown = box.high() - originDown - marginDown;
        return box.down() + originDown + theSpaceAboveTheLine(roomDown, stackHigh);
    }

    private double theShareOfTheSpareRoomBefore(double spareRoom) {
        return switch (align) {
            case LEFT -> 0;
            case CENTRE -> spareRoom / 2;
            case RIGHT -> spareRoom;
        };
    }

    private double theSpaceAboveTheLine(double roomDown, double lineHigh) {
        double spareRoom = roomDown - lineHigh;
        return switch (valign) {
            case TOP -> 0;
            case MIDDLE -> spareRoom / 2;
            case BOTTOM -> spareRoom;
        };
    }

    public double roomForEachLine(Placement box) {
        return wraps ? box.wide() - originAcross - marginAcross : Double.POSITIVE_INFINITY;
    }

    public TextLayout withOrigin(int across, int down) {
        return new TextLayout(across, down, marginAcross, marginDown, align, valign, shadowAcross, shadowDown, wraps);
    }

    public TextLayout withMargin(int across, int down) {
        return new TextLayout(originAcross, originDown, across, down, align, valign, shadowAcross, shadowDown, wraps);
    }

    public TextLayout aligned(TextAlignment across, TextVerticalAlignment down) {
        return new TextLayout(originAcross, originDown, marginAcross, marginDown, across, down, shadowAcross, shadowDown,
                wraps);
    }

    public TextLayout withShadow(int across, int down) {
        return new TextLayout(originAcross, originDown, marginAcross, marginDown, align, valign, across, down, wraps);
    }

    public TextLayout wrapping(boolean breaksALongLine) {
        return new TextLayout(originAcross, originDown, marginAcross, marginDown, align, valign, shadowAcross,
                shadowDown, breaksALongLine);
    }
}
