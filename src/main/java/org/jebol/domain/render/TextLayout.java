package org.jebol.domain.render;

public record TextLayout(
        int originAcross, int originDown,
        int marginAcross, int marginDown,
        TextAlignment align, TextVerticalAlignment valign,
        int shadowAcross, int shadowDown) {

    private static final int THE_STANDARD_INSET = 2;

    public static final TextLayout STANDARD = new TextLayout(
            THE_STANDARD_INSET, THE_STANDARD_INSET, THE_STANDARD_INSET, THE_STANDARD_INSET,
            TextAlignment.LEFT, TextVerticalAlignment.TOP, 0, 0);

    public record LinePlacement(double across, double baseline) {
    }

    public boolean castsAShadow() {
        return shadowAcross != 0 || shadowDown != 0;
    }

    public LinePlacement whereTheLineGoes(Placement box, double lineWide, double ascent, double descent) {
        double left = box.across() + originAcross;
        double top = box.down() + originDown;
        double roomAcross = box.wide() - originAcross - marginAcross;
        double roomDown = box.high() - originDown - marginDown;
        return new LinePlacement(
                left + theShareOfTheSpareRoomBefore(roomAcross - lineWide),
                top + theSpaceAboveTheLine(roomDown, ascent + descent) + ascent);
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

    public TextLayout withOrigin(int across, int down) {
        return new TextLayout(across, down, marginAcross, marginDown, align, valign, shadowAcross, shadowDown);
    }

    public TextLayout withMargin(int across, int down) {
        return new TextLayout(originAcross, originDown, across, down, align, valign, shadowAcross, shadowDown);
    }

    public TextLayout aligned(TextAlignment across, TextVerticalAlignment down) {
        return new TextLayout(originAcross, originDown, marginAcross, marginDown, across, down, shadowAcross, shadowDown);
    }

    public TextLayout withShadow(int across, int down) {
        return new TextLayout(originAcross, originDown, marginAcross, marginDown, align, valign, across, down);
    }
}
