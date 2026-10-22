package org.jebol.domain.render;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnEffectPaintsNothingAsInEveryR3FromTheSourceTest {

    private final Interpreter interpreter = AnInterpreter.withBounds(
            Bounds.standard().grant(HostService.WINDOWS));

    private List<PaintInstruction> paint(String gobSource) {
        interpreter.defineNewWordsIn(gobSource);
        GobValue gob = (GobValue) interpreter.run(gobSource).value();
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        return PaintList.of(gob, dialect).instructions();
    }

    private List<PaintInstruction> drawing(String drawBlock) {
        return paint("make gob! [size: 100x100 draw: [" + drawBlock + "]]");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "effect 0x0 50x50 [invert]",
            "effect 10x10 20x20 [gradient 0x1 0.0.150 0.0.50 blur]",
            "effect [grayscale]",
            "effect 0x0 50x50 []"})
    @DisplayName("DRAW's effect command is read and paints nothing")
    void theDrawCommandPaintsNothing(String drawBlock) {
        assertThat(drawing(drawBlock)).isEmpty();
    }

    @Test
    @DisplayName("and every command around it still paints, in order")
    void theCommandsAroundItStillPaint() {
        List<PaintInstruction> painted = drawing("""
                pen red box 1x1 20x20 effect 0x0 100x100 [invert tint 50] pen blue box 30x30 60x60""");

        assertThat(painted).hasSize(2);
        assertThat(((PaintInstruction.Drawn) painted.get(0)).painted().strokeColour())
                .contains(new Colour(255, 0, 0, 255));
        assertThat(((PaintInstruction.Drawn) painted.get(1)).painted().strokeColour())
                .contains(new Colour(0, 0, 255, 255));
    }

    @Test
    @DisplayName("a gob whose content is an effect block paints nothing, and keeps its block")
    void anEffectGobPaintsNothing() {
        assertThat(paint("make gob! [size: 40x40 effect: [invert]]")).isEmpty();
        assertThat(interpreter.display(interpreter.run("""
                g: make gob! [size: 40x40 effect: [invert]]
                g/effect"""))).isEqualTo("[invert]");
    }

    @Test
    @DisplayName("and there is no EFFECT function to render one into an image, as in 3.22")
    void thereIsNoEffectFunction() {
        assertThat(interpreter.display(interpreter.run("value? 'effect"))).isEqualTo("#(false)");
    }
}
