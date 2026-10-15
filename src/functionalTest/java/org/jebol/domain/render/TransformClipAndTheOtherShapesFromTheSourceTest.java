package org.jebol.domain.render;

import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TransformClipAndTheOtherShapesFromTheSourceTest {

    private static final double CLOSE_ENOUGH = 0.0001;

    private static List<PaintInstruction> drawingOf(String drawBlock) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        String source = "make gob! [size: 100x100 draw: [" + drawBlock + "]]";
        interpreter.defineFreshWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        ObjectValue dialect =
                (ObjectValue) interpreter.run("system/dialects/draw").value();
        return PaintList.of(gob, dialect).instructions();
    }

    private static PaintInstruction.Drawn onlyDrawing(String drawBlock) {
        List<PaintInstruction> painted = drawingOf(drawBlock);
        assertThat(painted).hasSize(1);
        return (PaintInstruction.Drawn) painted.getFirst();
    }

    @Nested
    @DisplayName("TRANSFORM")
    class Transforming {

        @Test
        @DisplayName("turns about its centre, so a shape spins where it stands")
        void turnsAboutItsCentre() {
            Transform turned = onlyDrawing(
                    "transform 90 50x50 1 1 0x0 box 40x40 60x60").transform();

            assertThat(turned.acrossScale()).isCloseTo(0, within(CLOSE_ENOUGH));
            assertThat(turned.downSkew()).isCloseTo(1, within(CLOSE_ENOUGH));
            assertThat(turned.acrossSkew()).isCloseTo(-1, within(CLOSE_ENOUGH));
            assertThat(turned.downScale()).isCloseTo(0, within(CLOSE_ENOUGH));
            assertThat(turned.acrossMove()).isCloseTo(100, within(CLOSE_ENOUGH));
            assertThat(turned.downMove()).isCloseTo(0, within(CLOSE_ENOUGH));
        }

        @Test
        @DisplayName("scales about that same centre")
        void scalesAboutTheSameCentre() {
            Transform scaled = onlyDrawing(
                    "transform 0 50x50 2 2 0x0 box 40x40 60x60").transform();

            assertThat(scaled.acrossScale()).isCloseTo(2, within(CLOSE_ENOUGH));
            assertThat(scaled.downScale()).isCloseTo(2, within(CLOSE_ENOUGH));
            assertThat(scaled.acrossMove()).isCloseTo(-50, within(CLOSE_ENOUGH));
            assertThat(scaled.downMove()).isCloseTo(-50, within(CLOSE_ENOUGH));
        }

        @Test
        @DisplayName("and moves afterwards, so the move is not turned with the shape")
        void movesAfterwards() {
            Transform moved = onlyDrawing(
                    "transform 90 50x50 1 1 10x20 box 40x40 60x60").transform();

            assertThat(moved.acrossMove()).isCloseTo(110, within(CLOSE_ENOUGH));
            assertThat(moved.downMove()).isCloseTo(20, within(CLOSE_ENOUGH));
        }

        @Test
        @DisplayName("with nothing asked for, it changes nothing")
        void withNothingAskedForItChangesNothing() {
            assertThat(onlyDrawing("transform 0 0x0 1 1 0x0 box 10x10 20x20")
                    .transform().isNone()).isTrue();
        }

        @Test
        @DisplayName("and it multiplies with what was already standing")
        void itMultipliesWithWhatWasStanding() {
            Transform both = onlyDrawing(
                    "translate 5x5 transform 0 0x0 2 2 0x0 box 0x0 10x10").transform();

            assertThat(both.acrossScale()).isCloseTo(2, within(CLOSE_ENOUGH));
            assertThat(both.acrossMove()).isCloseTo(5, within(CLOSE_ENOUGH));
            assertThat(both.downMove()).isCloseTo(5, within(CLOSE_ENOUGH));
        }
    }

    @Nested
    @DisplayName("INVERT-MATRIX")
    class Inverting {

        @Test
        @DisplayName("undoes what is standing, so a move comes back to nothing")
        void undoesAMove() {
            Transform undone =
                    onlyDrawing("translate 20x30 invert-matrix box 0x0 10x10")
                            .transform();

            assertThat(undone.acrossMove()).isCloseTo(-20, within(CLOSE_ENOUGH));
            assertThat(undone.downMove()).isCloseTo(-30, within(CLOSE_ENOUGH));
        }

        @Test
        @DisplayName("and inverting a scale halves it back")
        void undoesAScale() {
            Transform undone = onlyDrawing("scale 4 4 invert-matrix box 0x0 10x10")
                    .transform();

            assertThat(undone.acrossScale()).isCloseTo(0.25, within(CLOSE_ENOUGH));
            assertThat(undone.downScale()).isCloseTo(0.25, within(CLOSE_ENOUGH));
        }

        @Test
        @DisplayName("twice over is where it started")
        void twiceOverIsWhereItStarted() {
            Transform back = onlyDrawing(
                    "translate 20x30 invert-matrix invert-matrix box 0x0 10x10")
                    .transform();

            assertThat(back.acrossMove()).isCloseTo(20, within(CLOSE_ENOUGH));
            assertThat(back.downMove()).isCloseTo(30, within(CLOSE_ENOUGH));
        }

        @Test
        @DisplayName("a transform with no undoing is left standing")
        void atransformWithNoUndoingIsLeftStanding() {
            Transform flattened = onlyDrawing("scale 0 1 invert-matrix box 0x0 10x10")
                    .transform();

            assertThat(flattened.acrossScale()).isZero();
            assertThat(flattened.downScale()).isCloseTo(1, within(CLOSE_ENOUGH));
        }
    }

    @Nested
    @DisplayName("CLIP")
    class Clipping {

        @Test
        @DisplayName("narrows what the rest of the block may paint on")
        void narrowsWhatFollows() {
            ClipRectangle own = onlyDrawing("clip 10x10 40x40 box 0x0 100x100")
                    .where().clip();

            assertThat(own.across()).isEqualTo(10);
            assertThat(own.down()).isEqualTo(10);
            assertThat(own.wide()).isEqualTo(30);
            assertThat(own.high()).isEqualTo(30);
        }

        @Test
        @DisplayName("and leaves what came before it alone")
        void leavesWhatCameBeforeAlone() {
            List<PaintInstruction> painted =
                    drawingOf("box 0x0 100x100 clip 10x10 40x40 box 0x0 100x100");

            assertThat(painted).hasSize(2);
            assertThat(painted.get(0).where().clip().wide()).isEqualTo(100);
            assertThat(painted.get(1).where().clip().wide()).isEqualTo(30);
        }

        @Test
        @DisplayName("never widens past the gob, however big the box is")
        void neverWidensPastTheGob() {
            ClipRectangle own =
                    onlyDrawing("clip 0x0 9999x9999 box 0x0 100x100").where().clip();

            assertThat(own.wide()).isEqualTo(100);
            assertThat(own.high()).isEqualTo(100);
        }

        @Test
        @DisplayName("and with no corners it opens back up to the whole gob")
        void withNoCornersItOpensBackUp() {
            List<PaintInstruction> painted =
                    drawingOf("clip 10x10 40x40 box 0x0 100x100 clip box 0x0 100x100");

            assertThat(painted).hasSize(2);
            assertThat(painted.get(0).where().clip().wide()).isEqualTo(30);
            assertThat(painted.get(1).where().clip().wide()).isEqualTo(100);
        }

        @Test
        @DisplayName("PUSH puts the clip back with everything else")
        void pushPutsTheClipBack() {
            List<PaintInstruction> painted = drawingOf(
                    "push [clip 10x10 40x40 box 0x0 100x100] box 0x0 100x100");

            assertThat(painted).hasSize(2);
            assertThat(painted.get(0).where().clip().wide()).isEqualTo(30);
            assertThat(painted.get(1).where().clip().wide()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("TRIANGLE")
    class Triangles {

        @Test
        @DisplayName("is a closed path through its three corners")
        void isAClosedPathThroughThreeCorners() {
            List<PathStep> path = onlyDrawing("triangle 10x10 90x10 50x90").path();

            assertThat(path).hasSize(4);
            assertThat(path.get(0)).isEqualTo(new PathStep.MoveTo(10, 10));
            assertThat(path.get(1)).isEqualTo(new PathStep.LineTo(90, 10));
            assertThat(path.get(2)).isEqualTo(new PathStep.LineTo(50, 90));
            assertThat(path.get(3).kind()).isEqualTo(PathStepKind.CLOSE);
        }

        @Test
        @DisplayName("and each missing corner has its own default")
        void eachMissingCornerHasItsOwnDefault() {
            List<PathStep> path = onlyDrawing("triangle").path();

            assertThat(path.get(0)).isEqualTo(new PathStep.MoveTo(0, 0));
            assertThat(path.get(1)).isEqualTo(new PathStep.LineTo(100, 100));
            assertThat(path.get(2)).isEqualTo(new PathStep.LineTo(0, 100));
        }

        @Test
        @DisplayName("its three colours are shaded across it, worked out here")
        void itsThreeColoursAreShadedAcrossIt() {
            List<PaintInstruction> painted =
                    drawingOf("triangle 10x10 90x10 50x90 255.0.0 0.255.0 0.0.255 1");

            assertThat(painted)
                    .as("a mesh of flat pieces, which both renderers draw the same")
                    .hasSizeGreaterThan(100);
            assertThat(painted).allSatisfy(piece ->
                    assertThat(((PaintInstruction.Drawn) piece).path())
                            .hasSizeBetween(4, 4));
        }

        @Test
        @DisplayName("and each piece is the blend of the corners where it sits")
        void eachPieceIsTheBlendWhereItSits() {
            List<PaintInstruction> painted =
                    drawingOf("pen off triangle 10x10 90x10 50x90 255.0.0 0.255.0 0.0.255 1");

            List<Colour> filled = painted.stream()
                    .map(PaintInstruction.Drawn.class::cast)
                    .map(piece -> piece.painted().fillColour().orElseThrow())
                    .toList();
            assertThat(filled).anySatisfy(colour ->
                    assertThat(colour.red()).isGreaterThan(200));
            assertThat(filled).anySatisfy(colour ->
                    assertThat(colour.green()).isGreaterThan(200));
            assertThat(filled).anySatisfy(colour ->
                    assertThat(colour.blue()).isGreaterThan(200));
            assertThat(filled).anySatisfy(colour -> {
                assertThat(colour.red()).isBetween(40, 200);
                assertThat(colour.green()).isBetween(40, 200);
            });
        }

        @Test
        @DisplayName("with no colours it is the plain outline it always was")
        void withNoColoursItIsThePlainOutline() {
            assertThat(onlyDrawing("triangle 10x10 90x10 50x90").path()).hasSize(4);
        }
    }

    @Nested
    @DisplayName("SPLINE")
    class Splines {

        @Test
        @DisplayName("passes through every point it is given")
        void passesThroughEveryPoint() {
            List<PathStep> path =
                    onlyDrawing("spline 20 10x10 50x80 90x10").path();

            assertThat(path.getFirst()).isEqualTo(new PathStep.MoveTo(10, 10));
            assertThat(path).hasSize(3);
            assertThat(path.get(1)).isInstanceOf(PathStep.CubicTo.class);
            PathStep.CubicTo first = (PathStep.CubicTo) path.get(1);
            assertThat(first.across()).isCloseTo(50, within(CLOSE_ENOUGH));
            assertThat(first.down()).isCloseTo(80, within(CLOSE_ENOUGH));
            PathStep.CubicTo second = (PathStep.CubicTo) path.get(2);
            assertThat(second.across()).isCloseTo(90, within(CLOSE_ENOUGH));
            assertThat(second.down()).isCloseTo(10, within(CLOSE_ENOUGH));
        }

        @Test
        @DisplayName("and a closed one joins the end back to the start")
        void aclosedOneJoinsTheEndToTheStart() {
            List<PathStep> path =
                    onlyDrawing("spline 20 closed 10x10 50x80 90x10").path();

            assertThat(path.getLast().kind()).isEqualTo(PathStepKind.CLOSE);
            assertThat(path).hasSize(5);
        }

        @Test
        @DisplayName("two points is a curve as much as twenty are")
        void twoPointsIsACurve() {
            List<PathStep> path = onlyDrawing("spline 20 10x10 90x90").path();

            assertThat(path).hasSize(2);
            assertThat(path.get(1)).isInstanceOf(PathStep.CubicTo.class);
        }

        @Test
        @DisplayName("and fewer than two points paints nothing at all")
        void fewerThanTwoPointsPaintsNothing() {
            assertThat(drawingOf("spline 20 10x10")).isEmpty();
            assertThat(drawingOf("spline 20")).isEmpty();
        }
    }
}
