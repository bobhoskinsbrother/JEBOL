package org.jebol.domain.render;

import org.jebol.application.TheCarriedFont;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheDefaultFontRendererFromTheSourceTest {

    private static final Colour BLACK = Colour.BLACK;

    private static final DefaultFontRenderer THE_FONT = TheCarriedFont.font().renderer();

    private static TextExtent measureTheExtentOf(String text, double size) {
        return THE_FONT.measure().textExtent(new TextRun(text, BLACK, size, false, false));
    }

    @Test
    @DisplayName("the font this build carries reads, and says which family it is")
    void readsTheFont() {
        assertThat(THE_FONT.familyName()).isEqualTo("Noto Sans");
    }

    @Test
    @DisplayName("a wide letter is wider than a narrow one, and both are wider than nothing")
    void givesEachLetterItsOwnWidth() {
        assertThat(measureTheExtentOf("", 12).wide()).isZero();
        assertThat(measureTheExtentOf("M", 12).wide()).isPositive();
        assertThat(measureTheExtentOf("i", 12).wide()).isPositive();
        assertThat(measureTheExtentOf("M", 12).wide()).isGreaterThan(measureTheExtentOf("i", 12).wide());
        assertThat(measureTheExtentOf(" ", 12).wide()).isPositive();
    }

    @Test
    @DisplayName("a string is as wide as its letters added up")
    void addsUpTheLettersOfAString() {
        assertThat(measureTheExtentOf("MW", 12).wide())
                .isCloseTo(measureTheExtentOf("M", 12).wide() + measureTheExtentOf("W", 12).wide(),
                        org.assertj.core.data.Offset.offset(1e-9));
        assertThat(measureTheExtentOf("MMM", 12).wide())
                .isCloseTo(3 * measureTheExtentOf("M", 12).wide(),
                        org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("the width doubles when the size does")
    void scalesTheWidthWithTheSize() {
        assertThat(measureTheExtentOf("A quick fox", 24).wide())
                .isCloseTo(2 * measureTheExtentOf("A quick fox", 12).wide(),
                        org.assertj.core.data.Offset.offset(1e-9));
        assertThat(measureTheExtentOf("A quick fox", 24).ascent())
                .isCloseTo(2 * measureTheExtentOf("A quick fox", 12).ascent(),
                        org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("a line has an ascent above the baseline and a descent below it")
    void givesALineBothSides() {
        TextExtent extent = measureTheExtentOf("Ag", 12);

        assertThat(extent.ascent()).isPositive();
        assertThat(extent.descent()).isPositive();
        assertThat(extent.high()).isEqualTo(extent.ascent() + extent.descent());
    }

    @Test
    @DisplayName("a character the font has not got still takes room, as its mark does")
    void makesRoomForACharacterItHasNotGot() {
        assertThat(measureTheExtentOf("\uE000", 12).wide()).isPositive();
    }

    @Test
    @DisplayName("every printable character the examples use is in it")
    void holdsEveryPrintableCharacter() {
        for (int character = 0x20; character < 0x7F; character++) {
            assertThat(measureTheExtentOf(String.valueOf((char) character), 12).wide())
                    .as("the width of %s", (char) character)
                    .isPositive();
        }
    }

    @Test
    @DisplayName("the same bytes read twice measure the same")
    void readsTheSameTwice() {
        assertThat(TheCarriedFont.font().renderer().measure().textExtent(
                new TextRun("survey", BLACK, 20, false, false)).wide())
                .isEqualTo(measureTheExtentOf("survey", 20).wide());
    }
}
