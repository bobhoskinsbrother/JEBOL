package org.jebol.domain.eval.blend2d;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DPaintTest {

    private static final int RED = 0xFFFF0000;

    private static final int GREEN = 0xFF00FF00;

    private static final int BLUE = 0xFF0000FF;

    private static Blend2DGradient redToBlue() {
        Blend2DGradient gradient = Blend2DGradient.linear(0, 0, 10, 0);
        gradient.addStop(0, RED);
        gradient.addStop(1, BLUE);
        return gradient;
    }

    @Test
    @DisplayName("a linear gradient is its first stop at its start, its last at its end, and halfway between in the middle")
    void runsBetweenItsStops() {
        Blend2DGradient gradient = redToBlue();
        assertThat(gradient.colourAt(0, 3)).isEqualTo(RED);
        assertThat(gradient.colourAt(10, -7)).isEqualTo(BLUE);
        assertThat(gradient.colourAt(5, 0)).isEqualTo(0xFF800080);
    }

    @Test
    @DisplayName("past its ends a gradient pads, repeats or reflects as its extend says")
    void extendsPastItsEnds() {
        Blend2DGradient padded = redToBlue();
        assertThat(padded.colourAt(12.5, 0)).isEqualTo(BLUE);
        Blend2DGradient repeated = redToBlue();
        repeated.setExtend(Blend2DGradient.Extend.REPEAT);
        assertThat(repeated.colourAt(12.5, 0)).isEqualTo(redToBlue().colourAt(2.5, 0));
        Blend2DGradient reflected = redToBlue();
        reflected.setExtend(Blend2DGradient.Extend.REFLECT);
        assertThat(reflected.colourAt(12.5, 0)).isEqualTo(redToBlue().colourAt(7.5, 0));
    }

    @Test
    @DisplayName("a stop outside 0 to 1 is refused, as Blend2D refuses it, and a gradient with no stops is transparent")
    void refusesAStopOutsideTheRun() {
        Blend2DGradient gradient = Blend2DGradient.linear(0, 0, 10, 0);
        gradient.addStop(-1, RED);
        gradient.addStop(2, BLUE);
        assertThat(gradient.colourAt(5, 0)).isZero();
        gradient.addStop(0.5, GREEN);
        assertThat(gradient.colourAt(0, 0)).isEqualTo(GREEN);
    }

    @Test
    @DisplayName("two stops at one offset make a sharp edge, and a third at that offset replaces the second")
    void keepsAtMostTwoStopsAtOneOffset() {
        Blend2DGradient gradient = Blend2DGradient.linear(0, 0, 10, 0);
        gradient.addStop(0.5, RED);
        gradient.addStop(0.5, BLUE);
        assertThat(gradient.colourAt(4.9, 0)).isEqualTo(RED);
        assertThat(gradient.colourAt(5.1, 0)).isEqualTo(BLUE);
        gradient.addStop(0.5, GREEN);
        assertThat(gradient.colourAt(5.1, 0)).isEqualTo(GREEN);
        assertThat(gradient.colourAt(4.9, 0)).isEqualTo(RED);
    }

    @Test
    @DisplayName("a radial gradient whose focus is its centre is its first stop there and its last a radius out")
    void runsOutFromItsCentre() {
        Blend2DGradient gradient = Blend2DGradient.radial(10, 10, 10, 10, 5);
        gradient.addStop(0, RED);
        gradient.addStop(1, BLUE);
        assertThat(gradient.colourAt(10, 10)).isEqualTo(RED);
        assertThat(gradient.colourAt(15, 10)).isEqualTo(BLUE);
        assertThat(gradient.colourAt(10, 7.5)).isEqualTo(0xFF800080);
    }

    @Test
    @DisplayName("a pattern samples its pixels at their centres, and pads, tiles or flips past its edges per axis")
    void samplesAndExtendsAPattern() {
        int[] pixels = {RED, GREEN, BLUE, 0xFFFFFFFF};
        Blend2DPattern tiled = new Blend2DPattern(pixels, 2, 2, 1);
        assertThat(tiled.premultipliedAt(0.5, 0.5)).isEqualTo(RED);
        assertThat(tiled.premultipliedAt(2.5, 0.5)).isEqualTo(RED);
        Blend2DPattern padded = new Blend2DPattern(pixels, 2, 2, 0);
        assertThat(padded.premultipliedAt(9.5, 0.5)).isEqualTo(GREEN);
        Blend2DPattern flipped = new Blend2DPattern(pixels, 2, 2, 2);
        assertThat(flipped.premultipliedAt(2.5, 0.5)).isEqualTo(GREEN);
        assertThat(tiled.premultipliedAt(1.0, 0.5)).isEqualTo(0xFF808000);
    }
}
