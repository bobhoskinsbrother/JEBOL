package org.jebol.domain.render;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AColoursFourthNumberIsHowOpaqueItIsFromTheSourceTest {

    private final Interpreter interpreter = Interpreter.withBounds(
            Bounds.standard().granting(HostService.WINDOWS));

    private PaintInstruction.Drawn theOnlyShapeOf(String drawBlock) {
        String source = "make gob! [size: 100x100 draw: [" + drawBlock + "]]";
        interpreter.defineFreshWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        List<PaintInstruction> painted = PaintList.of(gob, dialect).instructions();
        assertThat(painted).hasSize(1);
        return (PaintInstruction.Drawn) painted.getFirst();
    }

    @ParameterizedTest(name = "fill-pen {0} is {1} opaque")
    @CsvSource({
            "0.0.0,       255",
            "0.0.0.255,   255",
            "0.0.0.254,   254",
            "0.0.0.128,   128",
            "0.0.0.1,     1",
            "0.0.0.0,     0",
            "10.20.30.40, 40",
    })
    @DisplayName("a fill-pen's fourth number is its opacity, and three numbers are solid")
    void aFillPensFourthNumberIsItsOpacity(String colour, int opacity) {
        assertThat(theOnlyShapeOf("fill-pen " + colour + " box 0x0 10x10")
                .painted().fillColour().orElseThrow().opacity()).isEqualTo(opacity);
    }

    @ParameterizedTest(name = "pen {0} is {1} opaque")
    @CsvSource({
            "200.0.0,     255",
            "200.0.0.128, 128",
            "200.0.0.0,   0",
    })
    @DisplayName("and so is a pen's")
    void aPensFourthNumberIsItsOpacity(String colour, int opacity) {
        assertThat(theOnlyShapeOf("pen " + colour + " box 0x0 10x10")
                .painted().strokeColour().orElseThrow().opacity()).isEqualTo(opacity);
    }

    @Test
    @DisplayName("the colour is kept apart from its opacity, so 0.0.0.128 is still black")
    void theColourIsKept() {
        Colour painted = theOnlyShapeOf("fill-pen 10.20.30.128 box 0x0 10x10")
                .painted().fillColour().orElseThrow();

        assertThat(painted).isEqualTo(new Colour(10, 20, 30, 128));
        assertThat(painted.red()).isEqualTo(10);
        assertThat(painted.green()).isEqualTo(20);
        assertThat(painted.blue()).isEqualTo(30);
    }

    @Test
    @DisplayName("the 2010 GUI's scroller track, 0.0.0.128, is half-transparent black")
    void theScrollerTrackIsHalfTransparent() {
        assertThat(theOnlyShapeOf("fill-pen 0.0.0.128 box 0x0 21x21 3")
                .painted().fillColour()).contains(new Colour(0, 0, 0, 128));
    }

    @Test
    @DisplayName("a gradient's colours keep their opacity too")
    void aGradientsColoursKeepTheirOpacity() {
        Gradient gradient = theOnlyShapeOf(
                "grad-pen linear normal 50x50 0 100 0 1 1 [255.0.0.64 0.0.255] box 0x0 100x100")
                .painted().fillGradient().orElseThrow();

        assertThat(gradient.colours()).containsExactly(new Colour(255, 0, 0, 64), new Colour(0, 0, 255));
    }

    @Test
    @DisplayName("a colour of three numbers is equal to the same colour written solid with four")
    void threeNumbersAreSolid() {
        assertThat(new Colour(1, 2, 3)).isEqualTo(new Colour(1, 2, 3, 255));
    }

    @ParameterizedTest(name = "an opacity of {0} is held as {1}")
    @CsvSource({"-1, 0", "0, 0", "255, 255", "256, 255"})
    @DisplayName("an opacity outside 0 to 255 is held at the nearer end, as each colour channel is")
    void opacityIsClamped(int asked, int held) {
        assertThat(new Colour(0, 0, 0, asked).opacity()).isEqualTo(held);
    }

    @ParameterizedTest(name = "{0} is written for a browser as {1}")
    @CsvSource(delimiter = '|', value = {
            "200;100;50;255 | #c86432",
            "0;0;0;128      | rgba(0,0,0,0.502)",
            "0;0;0;0        | rgba(0,0,0,0)",
            "255;255;255;1  | rgba(255,255,255,0.004)",
    })
    @DisplayName("a browser is handed #rrggbb for a solid colour and rgba for one it can see through")
    void aBrowserIsHandedRgbaWhenNeeded(String channels, String css) {
        String[] parts = channels.split(";");
        Colour colour = new Colour(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));

        assertThat(colour.asCss()).isEqualTo(css);
    }
}
