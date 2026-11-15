package org.jebol.domain.eval.blend2d;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Blend2DStrokeTest {

    private static final int WHITE = 0xFFFFFFFF;

    private static final int RED = 0xFFFF0000;

    private static Blend2DSurface whiteSurface(int wide, int high) {
        int[] pixels = new int[wide * high];
        Arrays.fill(pixels, WHITE);
        return new Blend2DSurface(wide, high, pixels);
    }

    private static Blend2DCanvas redPenOn(Blend2DSurface surface, double width) {
        Blend2DCanvas canvas = new Blend2DCanvas(surface);
        canvas.setStrokeColour(RED);
        canvas.setStrokeWidth(width);
        return canvas;
    }

    private static Blend2DShape line(double... points) {
        return Blend2DShape.polylines(List.of(points));
    }

    @Test
    @DisplayName("a horizontal line on a pixel boundary, 2 wide, covers exactly the row either side and stops at its ends with butt caps")
    void strokesAWholePixelLine() {
        Blend2DSurface surface = whiteSurface(12, 8);
        redPenOn(surface, 2).stroke(line(2, 4, 10, 4));
        for (int down = 0; down < 8; down++) {
            for (int across = 0; across < 12; across++) {
                boolean isCovered = down >= 3 && down < 5 && across >= 2 && across < 10;
                assertThat(surface.pixelAt(across, down)).as(across + "x" + down).isEqualTo(isCovered ? RED : WHITE);
            }
        }
    }

    @Test
    @DisplayName("a square cap reaches half the width past each end, where a butt cap stops at the end")
    void squareCapsReachPastTheEnds() {
        Blend2DSurface butt = whiteSurface(12, 8);
        redPenOn(butt, 2).stroke(line(2, 4, 10, 4));
        Blend2DSurface square = whiteSurface(12, 8);
        Blend2DCanvas canvas = redPenOn(square, 2);
        canvas.setStrokeCaps(PathStroker.CAP_SQUARE, PathStroker.CAP_SQUARE);
        canvas.stroke(line(2, 4, 10, 4));
        assertThat(butt.pixelAt(1, 4)).isEqualTo(WHITE);
        assertThat(square.pixelAt(1, 4)).isEqualTo(RED);
        assertThat(square.pixelAt(10, 4)).isEqualTo(RED);
        assertThat(square.pixelAt(0, 4)).isEqualTo(WHITE);
    }

    @Test
    @DisplayName("the outline of a box on whole pixels, 2 wide, is a frame a pixel either side of its edges and leaves its middle alone")
    void strokesABoxAsAFrame() {
        Blend2DSurface surface = whiteSurface(14, 14);
        redPenOn(surface, 2).stroke(Blend2DShape.box(3, 3, 8, 8));
        assertThat(surface.pixelAt(2, 2)).isEqualTo(RED);
        assertThat(surface.pixelAt(11, 11)).isEqualTo(RED);
        assertThat(surface.pixelAt(7, 2)).isEqualTo(RED);
        assertThat(surface.pixelAt(7, 7)).isEqualTo(WHITE);
        assertThat(surface.pixelAt(1, 1)).isEqualTo(WHITE);
    }

    @Test
    @DisplayName("a sharp turn mitres to a point under miter-clip, and is cut short under bevel")
    void joinsASharpTurnByTheJoin() {
        Blend2DSurface mitred = whiteSurface(30, 30);
        redPenOn(mitred, 6).stroke(line(4, 25, 15, 5, 26, 25));
        Blend2DSurface bevelled = whiteSurface(30, 30);
        Blend2DCanvas canvas = redPenOn(bevelled, 6);
        canvas.setStrokeJoin(PathStroker.JOIN_BEVEL);
        canvas.stroke(line(4, 25, 15, 5, 26, 25));
        assertThat(mitred.pixelAt(15, 1)).isNotEqualTo(WHITE);
        assertThat(bevelled.pixelAt(15, 1)).isEqualTo(WHITE);
    }

    @Test
    @DisplayName("the outline of a circle leaves its middle and its far corners untouched and covers its rim")
    void strokesACircleAsARing() {
        Blend2DSurface surface = whiteSurface(24, 24);
        redPenOn(surface, 2).stroke(Blend2DShape.ellipse(12, 12, 8, 8));
        assertThat(surface.pixelAt(12, 12)).isEqualTo(WHITE);
        assertThat(surface.pixelAt(0, 0)).isEqualTo(WHITE);
        assertThat(surface.pixelAt(19, 12)).isEqualTo(RED);
        assertThat(surface.pixelAt(12, 4)).isEqualTo(RED);
    }

    @Test
    @DisplayName("a line shorter than the stroker's epsilon draws nothing, and nor does a stroke with nothing to stroke")
    void strokesNothingWhenThereIsNothingToStroke() {
        Blend2DSurface surface = whiteSurface(8, 8);
        Blend2DCanvas canvas = redPenOn(surface, 2);
        canvas.stroke(line(4, 4, 4, 4));
        canvas.stroke(line(4, 4, 4 + 1e-12, 4));
        canvas.stroke(Blend2DShape.polylines(List.of()));
        assertThat(surface.pixels()).containsOnly(WHITE);
    }
}
