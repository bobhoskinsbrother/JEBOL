package org.jebol.domain.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextLayoutPlacesALineFromTheSourceTest {

    private static final double ASCENT = 10;

    private static final double DESCENT = 4;

    private final Placement aButton = new Placement(10, 20, 100, 40, ClipRectangle.wholeSurface(400, 400), Placement.OPAQUE);

    private TextLayout laidOut(TextAlignment align, TextVerticalAlignment valign) {
        return new TextLayout(2, 2, 2, 2, align, valign, 0, 0);
    }

    @ParameterizedTest(name = "{0} puts a line {1} wide at {2} across")
    @CsvSource({
            "LEFT,   40,  12",
            "CENTRE, 40,  40",
            "RIGHT,  40,  68",
            "LEFT,   96,  12",
            "CENTRE, 96,  12",
            "RIGHT,  96,  12",
            "CENTRE, 97,  11.5",
            "RIGHT,  97,  11",
            "CENTRE, 120, 0",
            "RIGHT,  120, -12",
            "CENTRE, 0,   60",
            "RIGHT,  0,   108",
    })
    @DisplayName("across, the line sits in the box less the origin and the margin, at the left, centre or right")
    void acrossFollowsTheAlignment(TextAlignment align, double lineWide, double across) {
        assertThat(laidOut(align, TextVerticalAlignment.TOP)
                .whereTheLineGoes(aButton, lineWide, ASCENT, DESCENT).across())
                .isEqualTo(across);
    }

    @ParameterizedTest(name = "{0} puts the baseline at {1}")
    @CsvSource({
            "TOP,    32",
            "MIDDLE, 43",
            "BOTTOM, 54",
    })
    @DisplayName("down, the line's top, middle or bottom meets the box's, and the baseline is that plus the ascent")
    void downFollowsTheVerticalAlignment(TextVerticalAlignment valign, double baseline) {
        assertThat(laidOut(TextAlignment.LEFT, valign)
                .whereTheLineGoes(aButton, 40, ASCENT, DESCENT).baseline())
                .isEqualTo(baseline);
    }

    @Test
    @DisplayName("a line taller than the box is centred on it, overhanging both edges alike")
    void aTallLineOverhangsEvenly() {
        double baseline = laidOut(TextAlignment.LEFT, TextVerticalAlignment.MIDDLE)
                .whereTheLineGoes(aButton, 40, 30, 16).baseline();

        assertThat(baseline - 30).as("the line's top").isEqualTo(17);
        assertThat(baseline + 16).as("the line's bottom").isEqualTo(63);
    }

    @Test
    @DisplayName("an origin moves the space in from the top left, which is how the radio's label clears its circle")
    void anOriginMovesTheSpace() {
        TextLayout radio = new TextLayout(18, 0, 2, 2,
                TextAlignment.LEFT, TextVerticalAlignment.MIDDLE, 0, 0);

        TextLayout.LinePlacement placed = radio.whereTheLineGoes(aButton, 40, ASCENT, DESCENT);

        assertThat(placed.across()).isEqualTo(28);
        assertThat(placed.baseline()).isEqualTo(20 + (38 - 14) / 2.0 + ASCENT);
    }

    @Test
    @DisplayName("no origin and no margin give the line the whole box")
    void nothingTakenGivesTheWholeBox() {
        TextLayout centred = new TextLayout(0, 0, 0, 0,
                TextAlignment.CENTRE, TextVerticalAlignment.MIDDLE, 0, 0);

        TextLayout.LinePlacement placed = centred.whereTheLineGoes(aButton, 40, ASCENT, DESCENT);

        assertThat(placed.across()).isEqualTo(40);
        assertThat(placed.baseline()).isEqualTo(20 + (40 - 14) / 2.0 + ASCENT);
    }

    @Test
    @DisplayName("a box with no room left still places the line, at its origin")
    void aBoxWithNoRoom() {
        Placement nothing = new Placement(10, 20, 0, 0, ClipRectangle.wholeSurface(400, 400), Placement.OPAQUE);

        TextLayout.LinePlacement placed = TextLayout.STANDARD.whereTheLineGoes(nothing, 40, ASCENT, DESCENT);

        assertThat(placed.across()).isEqualTo(12);
        assertThat(placed.baseline()).isEqualTo(32);
    }
}
