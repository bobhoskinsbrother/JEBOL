package org.jebol.domain.eval.blend2d;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DCurveFillTest {

    private static final int WHITE = 0xFFFFFFFF;

    private static final int RED = 0xFFFF0000;

    private static Blend2DSurface whiteSurface(int wide, int high) {
        int[] pixels = new int[wide * high];
        Arrays.fill(pixels, WHITE);
        return new Blend2DSurface(wide, high, pixels);
    }

    private static Blend2DCanvas redCanvasOn(Blend2DSurface surface) {
        Blend2DCanvas canvas = new Blend2DCanvas(surface);
        canvas.setFillColour(RED);
        return canvas;
    }

    @Test
    @DisplayName("a circle fills every pixel a pixel inside its radius solid and leaves every pixel a pixel outside it untouched")
    void fillsACircleByDistance() {
        Blend2DSurface surface = whiteSurface(20, 20);
        redCanvasOn(surface).fill(Blend2DShape.ellipse(10, 10, 8, 8));
        for (int down = 0; down < 20; down++) {
            for (int across = 0; across < 20; across++) {
                double distance = Math.hypot(across + 0.5 - 10, down + 0.5 - 10);
                if (distance < 7) {
                    assertThat(surface.pixelAt(across, down)).as(across + "x" + down).isEqualTo(RED);
                } else if (distance > 9) {
                    assertThat(surface.pixelAt(across, down)).as(across + "x" + down).isEqualTo(WHITE);
                }
            }
        }
    }

    @Test
    @DisplayName("a pie of a quarter turn from 0 degrees fills the quadrant below and right of its centre, and nothing above or left")
    void fillsAQuarterPie() {
        Blend2DSurface surface = whiteSurface(20, 20);
        redCanvasOn(surface).fill(Blend2DShape.arc(10, 10, 9, 9, 0, Math.PI / 2, Blend2DShape.ArcClosing.PIE));
        assertThat(surface.pixelAt(13, 13)).isEqualTo(RED);
        assertThat(surface.pixelAt(6, 6)).isEqualTo(WHITE);
        assertThat(surface.pixelAt(13, 6)).isEqualTo(WHITE);
        assertThat(surface.pixelAt(6, 13)).isEqualTo(WHITE);
    }

    @Test
    @DisplayName("a chord of a half turn is a half disc, as is the open arc of a half turn, since a fill closes it")
    void closesAnOpenArcAsAChord() {
        Blend2DSurface chord = whiteSurface(20, 20);
        redCanvasOn(chord).fill(Blend2DShape.arc(10, 10, 9, 9, 0, Math.PI, Blend2DShape.ArcClosing.CHORD));
        Blend2DSurface open = whiteSurface(20, 20);
        redCanvasOn(open).fill(Blend2DShape.arc(10, 10, 9, 9, 0, Math.PI, Blend2DShape.ArcClosing.OPEN));
        assertThat(open.pixels()).isEqualTo(chord.pixels());
        assertThat(chord.pixelAt(10, 14)).isEqualTo(RED);
        assertThat(chord.pixelAt(10, 5)).isEqualTo(WHITE);
    }

    @Test
    @DisplayName("a rounded box leaves its corner pixel uncovered but fills its edges' middles, and a radius of 0 is a plain box")
    void roundsABoxsCorners() {
        Blend2DSurface rounded = whiteSurface(20, 20);
        redCanvasOn(rounded).fill(Blend2DShape.roundedBox(2, 2, 16, 16, 5, 5));
        assertThat(rounded.pixelAt(2, 2)).isEqualTo(WHITE);
        assertThat(rounded.pixelAt(10, 2)).isEqualTo(RED);
        assertThat(rounded.pixelAt(2, 10)).isEqualTo(RED);
        Blend2DSurface square = whiteSurface(20, 20);
        redCanvasOn(square).fill(Blend2DShape.roundedBox(2, 2, 16, 16, 0, 0));
        Blend2DSurface box = whiteSurface(20, 20);
        redCanvasOn(box).fillRect(2, 2, 16, 16);
        assertThat(square.pixels()).isEqualTo(box.pixels());
    }

    @Test
    @DisplayName("a cubic curving outside the image is clipped at every side and still fills the pixels inside")
    void clipsACubicAtEverySide() {
        Blend2DSurface surface = whiteSurface(20, 20);
        redCanvasOn(surface).fill(Blend2DShape.cubics(new double[] {-10, -10, 40, 5, -20, 25, 30, 30}));
        assertThat(surface.pixels()).contains(RED);
        assertThat(surface.pixels()).contains(WHITE);
    }

    @Test
    @DisplayName("a cubic is cut at its extrema, inflections and cusp into a spline of monotonic pieces ending exactly on its end point")
    void splitsACubicAtItsTurningPoints() {
        double[] spline = CubicSpline.split(0, 0, new double[] {100, 100, -50, 100, 50, 0});
        assertThat((spline.length - 2) % 6).isZero();
        assertThat(spline.length).isGreaterThan(8);
        assertThat(spline[spline.length - 2]).isEqualTo(50.0);
        assertThat(spline[spline.length - 1]).isEqualTo(0.0);
        for (int at = 0; at + 6 < spline.length; at += 6) {
            double[] xs = {spline[at], spline[at + 2], spline[at + 4], spline[at + 6]};
            boolean isRising = xs[3] >= xs[0];
            for (int step = 1; step < 4; step++) {
                assertThat(isRising ? xs[step] >= xs[0] - 1e-9 : xs[step] <= xs[0] + 1e-9).isTrue();
            }
        }
        assertThat(CubicSpline.split(0, 0, new double[] {1, 1, 2, 2, 3, 3})).hasSize(8);
    }
}
