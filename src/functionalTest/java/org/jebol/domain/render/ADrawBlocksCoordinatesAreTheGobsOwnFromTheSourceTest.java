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

class ADrawBlocksCoordinatesAreTheGobsOwnFromTheSourceTest {

    private final Interpreter interpreter = Interpreter.withBounds(
            Bounds.standard().granting(HostService.WINDOWS));

    private List<PaintInstruction> paintedFrom(String treeBuiltBy) {
        interpreter.defineFreshWordsIn(treeBuiltBy);
        GobValue root = (GobValue) interpreter.run(treeBuiltBy).value();
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        return PaintList.of(root, dialect).instructions();
    }

    private PaintInstruction.Drawn theOnlyShapeIn(List<PaintInstruction> painted) {
        List<PaintInstruction.Drawn> shapes = painted.stream()
                .filter(PaintInstruction.Drawn.class::isInstance)
                .map(PaintInstruction.Drawn.class::cast)
                .toList();
        assertThat(shapes).hasSize(1);
        return shapes.getFirst();
    }

    private double[] whereOnTheSurface(PaintInstruction.Drawn shape, double across, double down) {
        Transform onTheSurface = shape.transform();
        return new double[] {
                onTheSurface.acrossScale() * across + onTheSurface.acrossSkew() * down
                        + onTheSurface.acrossMove(),
                onTheSurface.downSkew() * across + onTheSurface.downScale() * down
                        + onTheSurface.downMove()};
    }

    @ParameterizedTest(name = "a child at {0}x{1} paints its box's corner at {2}x{3}")
    @CsvSource({
            "0, 0, 1, 1",
            "1, 1, 2, 2",
            "30, 40, 31, 41",
            "149, 149, 150, 150",
    })
    @DisplayName("a draw block on a child gob is painted where the child sits")
    void aChildsDrawBlockIsPaintedWhereTheChildSits(int across, int down, int expectedAcross, int expectedDown) {
        PaintInstruction.Drawn box = theOnlyShapeIn(paintedFrom("""
                parent: make gob! [size: 200x200]
                append parent make gob! [offset: %dx%d size: 50x50 draw: [box 1x1 10x10]]
                parent""".formatted(across, down)));

        assertThat(whereOnTheSurface(box, 1, 1)).containsExactly(expectedAcross, expectedDown);
    }

    @Test
    @DisplayName("the offsets of every gob between the surface and the drawing add up")
    void nestedOffsetsAddUp() {
        PaintInstruction.Drawn box = theOnlyShapeIn(paintedFrom("""
                parent: make gob! [size: 300x300]
                middle: make gob! [offset: 10x20 size: 200x200]
                append middle make gob! [offset: 30x40 size: 50x50 draw: [box 1x1 10x10]]
                append parent middle
                parent"""));

        assertThat(whereOnTheSurface(box, 1, 1)).containsExactly(41.0, 61.0);
    }

    @Test
    @DisplayName("a translate inside the block moves the shape from the gob's corner, not the surface's")
    void aTranslateStartsFromTheGob() {
        PaintInstruction.Drawn box = theOnlyShapeIn(paintedFrom("""
                parent: make gob! [size: 200x200]
                append parent make gob! [offset: 30x40 size: 100x100 draw: [translate 5x5 box 0x0 10x10]]
                parent"""));

        assertThat(whereOnTheSurface(box, 0, 0)).containsExactly(35.0, 45.0);
    }

    @Test
    @DisplayName("reset-matrix returns to the gob's corner, not to the surface's")
    void resetMatrixReturnsToTheGob() {
        PaintInstruction.Drawn box = theOnlyShapeIn(paintedFrom("""
                parent: make gob! [size: 200x200]
                append parent make gob! [offset: 30x40 size: 100x100 draw: [translate 5x5 reset-matrix box 0x0 10x10]]
                parent"""));

        assertThat(whereOnTheSurface(box, 0, 0)).containsExactly(30.0, 40.0);
    }

    @Test
    @DisplayName("a gradient fill is carried with the shape, so it moves with the gob")
    void aGradientMovesWithItsShape() {
        PaintInstruction.Drawn box = theOnlyShapeIn(paintedFrom("""
                parent: make gob! [size: 200x200]
                append parent make gob! [offset: 30x40 size: 100x100 draw: [
                    pen off grad-pen linear 0x0 0 50 [255.0.0 0.0.255] box 0x0 50x50]]
                parent"""));

        assertThat(box.painted().fillGradient()).isPresent();
        assertThat(whereOnTheSurface(box, 0, 0)).containsExactly(30.0, 40.0);
    }
}
