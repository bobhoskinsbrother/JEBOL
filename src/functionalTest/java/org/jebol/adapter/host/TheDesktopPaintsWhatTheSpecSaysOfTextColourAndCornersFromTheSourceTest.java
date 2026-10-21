package org.jebol.adapter.host;

import org.jebol.application.Bounds;
import org.jebol.application.AnInterpreter;
import org.jebol.application.Interpreter;
import org.jebol.application.TheCarriedFont;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TheDesktopPaintsWhatTheSpecSaysOfTextColourAndCornersFromTheSourceTest {

    private static final int WIDE = 200;

    private static final int HIGH = 40;

    private static final Color WHITE = new Color(255, 255, 255);

    private final Interpreter interpreter = AnInterpreter.withBounds(
            Bounds.standard().grant(HostService.WINDOWS));

    private record Ink(int left, int top, int right, int bottom) {

        double middleAcross() {
            return (left + right) / 2.0;
        }

        double middleDown() {
            return (top + bottom) / 2.0;
        }
    }

    private BufferedImage paint(String source) {
        interpreter.defineNewWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        BufferedImage surface = new BufferedImage(WIDE, HIGH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D onto = surface.createGraphics();
        try {
            onto.setColor(Color.WHITE);
            onto.fillRect(0, 0, WIDE, HIGH);
            Java2DSurface.execute(onto, PaintList.of(gob, dialect),
                    TheCarriedFont.measurement(), TheCarriedFont.face());
        } finally {
            onto.dispose();
        }
        return surface;
    }

    private BufferedImage paintALabel(String richText) {
        return paint("""
                labelled: make gob! [size: 200x40]
                labelled/text: compose [%s]
                labelled""".formatted(richText));
    }

    private Color readTheColourAt(BufferedImage surface, int across, int down) {
        return new Color(surface.getRGB(across, down), true);
    }

    private boolean isInk(BufferedImage surface, int across, int down) {
        Color seen = readTheColourAt(surface, across, down);
        return seen.getRed() + seen.getGreen() + seen.getBlue() < 3 * 128;
    }

    private Ink findTheInkOf(BufferedImage surface) {
        int left = WIDE;
        int top = HIGH;
        int right = -1;
        int bottom = -1;
        for (int down = 0; down < HIGH; down++) {
            for (int across = 0; across < WIDE; across++) {
                if (isInk(surface, across, down)) {
                    left = Math.min(left, across);
                    top = Math.min(top, down);
                    right = Math.max(right, across);
                    bottom = Math.max(bottom, down);
                }
            }
        }
        assertThat(right).as("some ink was text at all").isGreaterThanOrEqualTo(0);
        return new Ink(left, top, right, bottom);
    }

    @Nested
    @DisplayName("text laid out by its para")
    class TheText {

        @Test
        @DisplayName("a centred para puts the label's ink in the middle of the gob, both ways")
        void centresALabelInTheMiddle() {
            Ink ink = findTheInkOf(paintALabel("""
                    para (make object! [origin: 0x0 margin: 0x0 align: 'center valign: 'middle])
                    "MMMM"
                    """));

            assertThat(ink.middleAcross()).isCloseTo(WIDE / 2.0, within(2.0));
            assertThat(ink.middleDown()).isCloseTo(HIGH / 2.0, within(3.0));
        }

        @Test
        @DisplayName("a centred line of two runs is centred as a whole")
        void centresTwoRunsTogether() {
            Ink ink = findTheInkOf(paintALabel("""
                    para (make object! [origin: 0x0 margin: 0x0 align: 'center valign: 'middle])
                    "MMMM" bold "MMMM"
                    """));

            assertThat(ink.middleAcross()).isCloseTo(WIDE / 2.0, within(2.0));
        }

        @Test
        @DisplayName("a right-aligned para ends the ink at the margin")
        void endsARightAlignedLabelAtTheMargin() {
            Ink ink = findTheInkOf(paintALabel("""
                    para (make object! [margin: 10x0 align: 'right])
                    "MMMM"
                    """));

            assertThat(ink.right()).isBetween(WIDE - 10 - 3, WIDE - 10);
        }

        @Test
        @DisplayName("the radio's origin of 18 starts the ink clear of the circle")
        void startsTheRadiosInkAt18() {
            Ink ink = findTheInkOf(paintALabel("""
                    para (make object! [origin: 18x0 valign: 'middle])
                    "Delta 10%"
                    """));

            assertThat(ink.left()).isBetween(18, 21);
        }

        @Test
        @DisplayName("a plain string starts at the standard origin, 2 across, near the top")
        void startsAPlainStringAtTheStandardOrigin() {
            Ink ink = findTheInkOf(paint("""
                    make gob! [size: 200x40 text: "MMMM"]"""));

            assertThat(ink.left()).isBetween(2, 4);
            assertThat(ink.top()).isBetween(2, 8);
        }

        @Test
        @DisplayName("a bottom-aligned para puts the ink down at the margin")
        void setsABottomAlignedLabelLow() {
            Ink ink = findTheInkOf(paintALabel("""
                    para (make object! [valign: 'bottom])
                    "MMMM"
                    """));

            assertThat(ink.bottom()).isBetween(HIGH - 2 - 6, HIGH - 2);
            assertThat(ink.top()).isGreaterThan(HIGH / 2 - 4);
        }
    }

    @Nested
    @DisplayName("text over several lines, and a caret in it")
    class LinesAndACaret {

        @Test
        @DisplayName("a newline puts the second line below the first, not after it")
        void startALineBelowAtANewline() {
            Ink ink = findTheInkOf(paintALabel("""
                    para (make object! [origin: 0x0 margin: 0x0 align: 'left valign: 'top])
                    "MM^/MM"
                    """));

            assertThat(ink.bottom() - ink.top()).as("two lines tall").isGreaterThan(18);
            assertThat(ink.right()).as("no wider than one line of MM").isLessThan(40);
        }

        @Test
        @DisplayName("a set caret is drawn as a line where the text after it starts")
        void drawASetCaret() {
            BufferedImage surface = paint("""
                    text: copy {MMMM}
                    labelled: make gob! [size: 200x40]
                    labelled/text: reduce [
                        'para make object! [origin: 0x0 margin: 0x0 align: 'left valign: 'top]
                        'caret make object! [caret: none start: none end: none]
                        text
                    ]
                    caret: select labelled/text 'caret
                    caret/caret: reduce [at labelled/text 4 text]
                    labelled""");

            assertThat(findTheInkOf(surface).left()).as("the caret before the first M").isBetween(0, 1);
        }

        @Test
        @DisplayName("and an unset caret draws nothing where the text starts")
        void leaveAnUnsetCaretUndrawn() {
            BufferedImage surface = paint("""
                    labelled: make gob! [size: 200x40]
                    labelled/text: reduce [
                        'para make object! [origin: 10x0 margin: 0x0 align: 'left valign: 'top]
                        'caret make object! [caret: [0 0] start: [0 0] end: [0 0]]
                        copy {MMMM}
                    ]
                    labelled""");

            assertThat(findTheInkOf(surface).left()).isGreaterThanOrEqualTo(10);
        }
    }

    @Nested
    @DisplayName("a font's shadow")
    class TheShadow {

        @Test
        @DisplayName("a white label with a 2x2 shadow writes black below and to the right of it")
        void writesInBlack() {
            BufferedImage surface = paintALabel("""
                    font (make object! [color: 255.255.255 size: 20 style: 'bold shadow: 2x2])
                    para (make object! [origin: 0x0 margin: 0x0 align: 'center valign: 'middle])
                    "MMMM"
                    """);

            Ink ink = findTheInkOf(surface);
            assertThat(ink.right() - ink.left()).as("the shadow is the only ink on white").isGreaterThan(20);
        }

        @Test
        @DisplayName("a white label with no shadow writes no ink on white")
        void leavesNoInkWhenAbsent() {
            BufferedImage surface = paintALabel("""
                    font (make object! [color: 255.255.255 size: 20 style: 'bold])
                    "MMMM"
                    """);

            boolean anyInk = false;
            for (int down = 0; down < HIGH; down++) {
                for (int across = 0; across < WIDE; across++) {
                    anyInk |= isInk(surface, across, down);
                }
            }
            assertThat(anyInk).isFalse();
        }
    }

    @Nested
    @DisplayName("a colour's opacity")
    class TheOpacity {

        @Test
        @DisplayName("half-transparent black over white is mid grey")
        void turnsHalfBlackGrey() {
            Color seen = readTheColourAt(paint("""
                    make gob! [size: 200x40 draw: [pen off fill-pen 0.0.0.128 box 0x0 200x40]]"""), 100, 20);

            assertThat(seen.getRed()).isCloseTo(127, within(2));
            assertThat(seen.getGreen()).isCloseTo(127, within(2));
            assertThat(seen.getBlue()).isCloseTo(127, within(2));
        }

        @Test
        @DisplayName("a fourth number of 0 paints nothing")
        void paintsNothingAtZero() {
            assertThat(readTheColourAt(paint("""
                    make gob! [size: 200x40 draw: [pen off fill-pen 0.0.0.0 box 0x0 200x40]]"""), 100, 20))
                    .isEqualTo(WHITE);
        }

        @Test
        @DisplayName("a fourth number of 255 is solid")
        void fullOpacityIsSolid() {
            assertThat(readTheColourAt(paint("""
                    make gob! [size: 200x40 draw: [pen off fill-pen 0.0.0.255 box 0x0 200x40]]"""), 100, 20))
                    .isEqualTo(new Color(0, 0, 0));
        }
    }

    @Nested
    @DisplayName("a box's corners")
    class TheCorners {

        @Test
        @DisplayName("a radius of 10 leaves the very corner unpainted and fills the middle")
        void stayEmptyWithARadius() {
            BufferedImage surface = paint("""
                    make gob! [size: 200x40 draw: [pen off fill-pen 255.0.0 box 0x0 39x39 10]]""");

            assertThat(readTheColourAt(surface, 1, 1)).isEqualTo(WHITE);
            assertThat(readTheColourAt(surface, 38, 1)).isEqualTo(WHITE);
            assertThat(readTheColourAt(surface, 1, 38)).isEqualTo(WHITE);
            assertThat(readTheColourAt(surface, 38, 38)).isEqualTo(WHITE);
            assertThat(readTheColourAt(surface, 20, 20)).isEqualTo(new Color(255, 0, 0));
            assertThat(readTheColourAt(surface, 20, 1)).isEqualTo(new Color(255, 0, 0));
        }

        @Test
        @DisplayName("and no radius fills right into the corner")
        void fillWithoutARadius() {
            assertThat(readTheColourAt(paint("""
                    make gob! [size: 200x40 draw: [pen off fill-pen 255.0.0 box 0x0 39x39]]"""), 1, 1))
                    .isEqualTo(new Color(255, 0, 0));
        }
    }
}
