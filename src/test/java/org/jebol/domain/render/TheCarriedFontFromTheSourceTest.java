package org.jebol.domain.render;

import org.jebol.application.TextMeasures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TheCarriedFontFromTheSourceTest {

    private static final Colour BLACK = Colour.BLACK;

    private static final CarriedFont THE_FONT = TextMeasures.theCarriedFont();

    private static TextExtent extentOf(String text, double size) {
        return THE_FONT.measure().extentOf(new TextRun(text, BLACK, size, false, false));
    }

    @Test
    @DisplayName("the font this build carries reads, and says which family it is")
    void theFontReads() {
        assertThat(THE_FONT.familyName()).isEqualTo("Noto Sans");
    }

    @Test
    @DisplayName("a wide letter is wider than a narrow one, and both are wider than nothing")
    void lettersHaveTheirOwnWidths() {
        assertThat(extentOf("", 12).wide()).isZero();
        assertThat(extentOf("M", 12).wide()).isPositive();
        assertThat(extentOf("i", 12).wide()).isPositive();
        assertThat(extentOf("M", 12).wide()).isGreaterThan(extentOf("i", 12).wide());
        assertThat(extentOf(" ", 12).wide()).isPositive();
    }

    @Test
    @DisplayName("a string is as wide as its letters added up")
    void aStringIsItsLettersAddedUp() {
        assertThat(extentOf("MW", 12).wide())
                .isCloseTo(extentOf("M", 12).wide() + extentOf("W", 12).wide(),
                        org.assertj.core.data.Offset.offset(1e-9));
        assertThat(extentOf("MMM", 12).wide())
                .isCloseTo(3 * extentOf("M", 12).wide(),
                        org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("the width doubles when the size does")
    void widthFollowsTheSize() {
        assertThat(extentOf("A quick fox", 24).wide())
                .isCloseTo(2 * extentOf("A quick fox", 12).wide(),
                        org.assertj.core.data.Offset.offset(1e-9));
        assertThat(extentOf("A quick fox", 24).ascent())
                .isCloseTo(2 * extentOf("A quick fox", 12).ascent(),
                        org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("a line has an ascent above the baseline and a descent below it")
    void aLineHasBothSides() {
        TextExtent extent = extentOf("Ag", 12);

        assertThat(extent.ascent()).isPositive();
        assertThat(extent.descent()).isPositive();
        assertThat(extent.high()).isEqualTo(extent.ascent() + extent.descent());
    }

    @Test
    @DisplayName("a character the font has not got still takes room, as its mark does")
    void aCharacterTheFontHasNotGotTakesRoom() {
        assertThat(extentOf("\uE000", 12).wide()).isPositive();
    }

    @Test
    @DisplayName("every printable character the examples use is in it")
    void everyPrintableCharacterIsInIt() {
        for (int character = 0x20; character < 0x7F; character++) {
            assertThat(extentOf(String.valueOf((char) character), 12).wide())
                    .as("the width of %s", (char) character)
                    .isPositive();
        }
    }

    @Test
    @DisplayName("the same bytes read twice measure the same")
    void readingTwiceIsTheSame() {
        assertThat(TextMeasures.theCarriedFont().measure().extentOf(
                new TextRun("survey", BLACK, 20, false, false)).wide())
                .isEqualTo(extentOf("survey", 20).wide());
    }
}
