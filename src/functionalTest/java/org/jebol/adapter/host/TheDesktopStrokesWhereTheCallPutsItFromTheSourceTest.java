package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.application.TheCarriedFont;
import org.jebol.domain.host.HostService;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class TheDesktopStrokesWhereTheCallPutsItFromTheSourceTest {

    private static final int WIDE = 240;
    private static final int HIGH = 60;
    private static final int RED = new Color(255, 0, 0).getRGB();
    private static final int WHITE = Color.WHITE.getRGB();

    private final Interpreter interpreter = AnInterpreter.withBounds(
            Bounds.standard().grant(HostService.WINDOWS));

    private BufferedImage paint(String drawBlock) {
        String source = "make gob! [size: 240x60 draw: [" + drawBlock + "]]";
        interpreter.defineNewWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        BufferedImage surface = new BufferedImage(WIDE, HIGH, BufferedImage.TYPE_INT_RGB);
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

    @ParameterizedTest(name = "the dash starting at {0}")
    @ValueSource(ints = {20, 42, 64})
    @DisplayName("a dash 14 long on whole pixels is 14 whole red pixels and stops dead, as a canvas draws it")
    void aDashEndsOnThePixelItIsGiven(int start) {
        BufferedImage painted = paint("pen red line-width 4 line-pattern 14 8 line 20x30 220x30");

        assertThat(painted.getRGB(start, 30)).as("the dash's first pixel").isEqualTo(RED);
        assertThat(painted.getRGB(start + 13, 30)).as("the dash's last pixel").isEqualTo(RED);
        assertThat(painted.getRGB(start + 14, 30)).as("the first pixel of the gap").isEqualTo(WHITE);
        assertThat(painted.getRGB(start + 21, 30)).as("the gap's last pixel").isEqualTo(WHITE);
    }

    @Test
    @DisplayName("a stroke two wide along a whole-pixel row covers the two rows either side of it, and nothing beyond")
    void aStrokeIsNotMovedTowardPixelCentres() {
        BufferedImage painted = paint("pen red line-width 2 line 20x30 220x30");

        assertThat(painted.getRGB(100, 28)).as("above the stroke").isEqualTo(WHITE);
        assertThat(painted.getRGB(100, 29)).as("the row above the line").isEqualTo(RED);
        assertThat(painted.getRGB(100, 30)).as("the row below the line").isEqualTo(RED);
        assertThat(painted.getRGB(100, 31)).as("below the stroke").isEqualTo(WHITE);
    }
}
