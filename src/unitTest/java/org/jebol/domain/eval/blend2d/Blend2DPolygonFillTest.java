package org.jebol.domain.eval.blend2d;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DPolygonFillTest {

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
    @DisplayName("a polygon on whole pixels fills exactly its pixels, with no coverage spilling past its edges")
    void fillsWholePixelsExactly() {
        Blend2DSurface surface = whiteSurface(6, 5);
        redCanvasOn(surface).fillPolygon(new double[] {1, 1, 4, 1, 4, 3, 1, 3});
        for (int down = 0; down < 5; down++) {
            for (int across = 0; across < 6; across++) {
                boolean isInside = across >= 1 && across < 4 && down >= 1 && down < 3;
                assertThat(surface.pixelAt(across, down)).as(across + "x" + down).isEqualTo(isInside ? RED : WHITE);
            }
        }
    }

    @Test
    @DisplayName("a pixel square on half pixels covers four pixels a quarter each, so all four get the same partial red")
    void splitsAPixelIntoQuarters() {
        Blend2DSurface surface = whiteSurface(4, 4);
        redCanvasOn(surface).fillPolygon(new double[] {0.5, 0.5, 1.5, 0.5, 1.5, 1.5, 0.5, 1.5});
        int quarter = surface.pixelAt(0, 0);
        assertThat(quarter).isNotIn(WHITE, RED);
        assertThat(new int[] {surface.pixelAt(1, 0), surface.pixelAt(0, 1), surface.pixelAt(1, 1)})
                .containsOnly(quarter);
        assertThat(surface.pixelAt(2, 0)).isEqualTo(WHITE);
        assertThat(surface.pixelAt(0, 2)).isEqualTo(WHITE);
    }

    @Test
    @DisplayName("a quarter of a pixel at full alpha masks by 63, the JIT's 0x80 times 255 shifted, not by 64")
    void masksAQuarterBySixtyThree() {
        int fullCoverage = 256 << 9;
        int quarter = fullCoverage + (64 << 9);
        assertThat(AnalyticFill.calculateMask(quarter, 0xFFFFFFFF, 255)).isEqualTo(63);
        assertThat(AnalyticFill.calculateMask(fullCoverage + (256 << 9), 0xFFFFFFFF, 255)).isEqualTo(255);
        assertThat(AnalyticFill.calculateMask(fullCoverage, 0xFFFFFFFF, 255)).isZero();
    }

    @Test
    @DisplayName("under non-zero winding coverage past one pixel stays full, and under even-odd it wraps back to empty")
    void windsByTheFillRule() {
        int twice = (256 << 9) + (512 << 9);
        assertThat(AnalyticFill.calculateMask(twice, 0xFFFFFFFF, 255)).isEqualTo(255);
        assertThat(AnalyticFill.calculateMask(twice, 0x1FF, 255)).isZero();
        assertThat(AnalyticFill.calculateMask(Integer.MIN_VALUE, 0xFFFFFFFF, 255)).isEqualTo(255);
    }

    @Test
    @DisplayName("the middle of a pentagram is wound twice, and non-zero fills it solid")
    void fillsAPentagramsMiddle() {
        Blend2DSurface surface = whiteSurface(40, 40);
        redCanvasOn(surface).fillPolygon(new double[] {20, 1, 31.8, 37.2, 1, 14.8, 39, 14.8, 8.2, 37.2});
        assertThat(surface.pixelAt(20, 22)).isEqualTo(RED);
        assertThat(surface.pixelAt(2, 2)).isEqualTo(WHITE);
    }

    @Test
    @DisplayName("a polygon past the image's left and top edges still fills the pixels at x 0 and y 0, through the clip's borders")
    void fillsUpToClippedBorders() {
        Blend2DSurface surface = whiteSurface(5, 5);
        redCanvasOn(surface).fillPolygon(new double[] {-10, -10, 3, -10, 3, 3, -10, 3});
        assertThat(surface.pixelAt(0, 0)).isEqualTo(RED);
        assertThat(surface.pixelAt(2, 2)).isEqualTo(RED);
        assertThat(surface.pixelAt(3, 3)).isEqualTo(WHITE);
    }

    @Test
    @DisplayName("a polygon wholly outside the image, or one with no area, leaves every pixel as it was")
    void leavesTheImageAloneWhenNothingIsInside() {
        Blend2DSurface surface = whiteSurface(5, 5);
        Blend2DCanvas canvas = redCanvasOn(surface);
        canvas.fillPolygon(new double[] {10, 10, 20, 10, 15, 20});
        canvas.fillPolygon(new double[] {1, 1, 4, 4});
        canvas.fillPolygon(new double[] {2, 2});
        assertThat(surface.pixels()).containsOnly(WHITE);
    }

    @Test
    @DisplayName("a box under a rotation is filled as a polygon, so a box turned a quarter is still a box")
    void fillsARotatedBox() {
        Blend2DSurface surface = whiteSurface(10, 10);
        Blend2DCanvas canvas = redCanvasOn(surface);
        canvas.rotateAbout(Math.PI / 2, 5, 5);
        canvas.fillRect(3, 4, 4, 2);
        assertThat(surface.pixelAt(5, 4)).isEqualTo(RED);
        assertThat(surface.pixelAt(4, 3)).isEqualTo(RED);
        assertThat(surface.pixelAt(3, 5)).isEqualTo(WHITE);
    }
}
