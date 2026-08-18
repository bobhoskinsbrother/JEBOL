package org.jebol.domain.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The DRAW dialect: what a gob's draw block paints.
 *
 * <p>Read by DELECT and turned into paint instructions here, once, so that a
 * desktop window, a browser and a phone execute the same drawing without any
 * of them knowing a dialect exists. Rebol's C fused the reading and the
 * drawing in {@code host-draw.c}, and the cost of that is visible in the
 * source we vendor: there is a win32 one and no posix one, so a stock R3 on
 * macOS or Linux draws nothing at all.
 *
 * <p>The defaults are the part worth reading twice, because nothing documents
 * them and they are read off the gob being drawn on. {@code box} with no
 * arguments fills the whole gob. {@code circle} with none is the biggest
 * circle that fits. {@code arc} with no length turns ninety degrees rather
 * than nothing. All three come from {@code host-draw.c} and all three are
 * pinned below.
 *
 * <p>Specified in {@code spec/draw.allium}.
 */
class DrawDialectFromTheSourceTest {

    /**
     * A gob, and the dialect its draw block is read against.
     *
     * <p>Both from one interpreter, because the dialect is
     * {@code system/dialects/draw} and {@code dial-draw.reb} builds it while
     * the library loads. A flattening given no dialect paints no draw blocks,
     * so a test that forgot it would see empty lists and read as a defect.
     */
    private record AGobAndItsDialect(
            GobValue gob, org.jebol.domain.value.ObjectValue dialect) {
    }

    private static AGobAndItsDialect gobAndDialectFrom(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.defineFreshWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        return new AGobAndItsDialect(gob,
                (org.jebol.domain.value.ObjectValue)
                        interpreter.run("system/dialects/draw").value());
    }

    private static GobValue gobFrom(String source) {
        return gobAndDialectFrom(source).gob();
    }

    /** What a draw block on a hundred-pixel-square gob paints. */
    private static List<PaintInstruction> drawingOf(String drawBlock) {
        AGobAndItsDialect drawn = gobAndDialectFrom(
                "make gob! [size: 100x100 draw: [" + drawBlock + "]]");
        return PaintList.of(drawn.gob(), drawn.dialect()).instructions();
    }

    private static PaintInstruction.Drawn onlyDrawing(String drawBlock) {
        List<PaintInstruction> painted = drawingOf(drawBlock);
        assertThat(painted).hasSize(1);
        return (PaintInstruction.Drawn) painted.getFirst();
    }

    @Nested
    @DisplayName("the state a block starts with")
    class TheStartingState {

        @Test
        @DisplayName("a black line one pixel wide, and no fill")
        void ablockStartsWithAVisibleLine() {
            PaintState painted = onlyDrawing("box 10x10 50x50").painted();

            assertThat(painted.strokeColour()).contains(Colour.BLACK);
            assertThat(painted.fillColour()).isEmpty();
            assertThat(painted.lineWidth()).isEqualTo(1);
        }

        @Test
        @DisplayName("which is what makes a one-line draw block draw something")
        void aoneLineBlockDrawsSomething() {
            assertThat(drawingOf("box 10x10 50x50")).hasSize(1);
        }

        @Test
        @DisplayName("and smoothing is on, because a jagged diagonal is nobody's default")
        void smoothingIsOn() {
            assertThat(onlyDrawing("box 10x10 50x50").painted().antiAliased()).isTrue();
        }
    }

    @Nested
    @DisplayName("the paint state")
    class ThePaintState {

        @Test
        @DisplayName("PEN sets the line colour")
        void pensetsTheLineColour() {
            assertThat(onlyDrawing("pen 200.100.50 box 10x10 50x50")
                    .painted().strokeColour())
                    .contains(new Colour(200, 100, 50));
        }

        @Test
        @DisplayName("FILL-PEN sets the inside colour")
        void fillpenSetsTheInside() {
            assertThat(onlyDrawing("fill-pen 0.128.0 box 10x10 50x50")
                    .painted().fillColour())
                    .contains(new Colour(0, 128, 0));
        }

        @Test
        @DisplayName("PEN with a logic turns the line off")
        void penoffTurnsTheLineOff() {
            assertThat(onlyDrawing("fill-pen 1.2.3 pen off box 10x10 50x50")
                    .painted().strokeColour()).isEmpty();
        }

        @Test
        @DisplayName("and a shape with neither paints nothing at all")
        void neitherPaintsNothing() {
            assertThat(drawingOf("pen off fill-pen off box 10x10 50x50"))
                    .as("`pen off fill-pen off` is a legal way to say it, so the "
                            + "shape is dropped rather than drawn invisibly")
                    .isEmpty();
        }

        @Test
        @DisplayName("the state stands until something changes it")
        void thestateStands() {
            List<PaintInstruction> painted =
                    drawingOf("pen 255.0.0 box 0x0 10x10 box 20x20 30x30");

            assertThat(painted).hasSize(2);
            assertThat(painted).allSatisfy(each ->
                    assertThat(((PaintInstruction.Drawn) each).painted().strokeColour())
                            .contains(new Colour(255, 0, 0)));
        }

        @Test
        @DisplayName("a width of zero is a width of one, and so is a negative")
        void awidthOfZeroIsOne() {
            // boot/draw.reb says so in the declaration: "Zero, or negative
            // values, produce a line-width of 1."
            assertThat(onlyDrawing("line-width 0 box 10x10 50x50")
                    .painted().lineWidth()).isEqualTo(1);
            assertThat(onlyDrawing("line-width -5 box 10x10 50x50")
                    .painted().lineWidth()).isEqualTo(1);
        }

        @Test
        @DisplayName("but a real width is kept")
        void arealWidthIsKept() {
            assertThat(onlyDrawing("line-width 3 box 10x10 50x50")
                    .painted().lineWidth()).isEqualTo(3);
        }

        @Test
        @DisplayName("LINE-CAP, LINE-JOIN and FILL-RULE take the dialect's own words")
        void thewordsAreTaken() {
            PaintState painted = onlyDrawing("""
                    line-cap rounded line-join bevel fill-rule even-odd \
                    box 10x10 50x50""").painted();

            assertThat(painted.lineCap()).isEqualTo(LineCap.ROUNDED);
            assertThat(painted.lineJoin()).isEqualTo(LineJoin.BEVEL);
            assertThat(painted.fillRule()).isEqualTo(FillRule.EVEN_ODD);
        }

        @Test
        @DisplayName("and ANTI-ALIAS can be turned off")
        void smoothingCanBeTurnedOff() {
            assertThat(onlyDrawing("anti-alias off box 10x10 50x50")
                    .painted().antiAliased()).isFalse();
        }
    }

    @Nested
    @DisplayName("the shapes")
    class TheShapes {

        @Test
        @DisplayName("BOX is a rectangle between two corners")
        void aboxIsARectangle() {
            List<PathStep> path = onlyDrawing("box 10x20 60x50").path();

            assertThat(path.getFirst()).isEqualTo(new PathStep.MoveTo(10, 20));
            assertThat(path).contains(new PathStep.LineTo(60, 20));
            assertThat(path).contains(new PathStep.LineTo(60, 50));
            assertThat(path).contains(new PathStep.LineTo(10, 50));
            assertThat(path.getLast()).isEqualTo(new PathStep.Close());
        }

        @Test
        @DisplayName("and BOX with no arguments fills the whole gob")
        void aboxWithNoArgumentsFillsTheGob() {
            // The default nothing documents. The C reads the corners off
            // `zero_pair` and `size_pair`, which is the gob being painted.
            List<PathStep> path = onlyDrawing("box").path();

            assertThat(path.getFirst()).isEqualTo(new PathStep.MoveTo(0, 0));
            assertThat(path).contains(new PathStep.LineTo(100, 100));
        }

        @Test
        @DisplayName("CIRCLE with one radius is round")
        void acircleWithOneRadiusIsRound() {
            assertThat(onlyDrawing("circle 50x50 20").path())
                    .containsExactly(new PathStep.EllipseAt(50, 50, 20, 20));
        }

        @Test
        @DisplayName("CIRCLE with two radii is an ellipse")
        void acircleWithTwoRadiiIsAnEllipse() {
            assertThat(onlyDrawing("circle 50x50 30 10").path())
                    .containsExactly(new PathStep.EllipseAt(50, 50, 30, 10));
        }

        @Test
        @DisplayName("and CIRCLE with none is the biggest that fits")
        void acircleWithNoRadiusIsTheBiggestThatFits() {
            // `min(centre.x, centre.y)` in the C, the centre being half the
            // gob: a hundred square gives a radius of fifty.
            assertThat(onlyDrawing("circle").path())
                    .containsExactly(new PathStep.EllipseAt(50, 50, 50, 50));
        }

        @Test
        @DisplayName("ELLIPSE is a corner and a diameter, where CIRCLE is a centre")
        void anellipseIsACornerAndADiameter() {
            assertThat(onlyDrawing("ellipse 10x20 40x60").path())
                    .as("the same shape said the other way round")
                    .containsExactly(new PathStep.EllipseAt(30, 50, 20, 30));
        }

        @Test
        @DisplayName("LINE runs through its points and does not close")
        void alineIsOpen() {
            assertThat(onlyDrawing("line 0x0 10x10 20x0").path()).containsExactly(
                    new PathStep.MoveTo(0, 0),
                    new PathStep.LineTo(10, 10),
                    new PathStep.LineTo(20, 0));
        }

        @Test
        @DisplayName("POLYGON runs through its points and does")
        void apolygonIsClosed() {
            assertThat(onlyDrawing("polygon 0x0 10x10 20x0").path()).containsExactly(
                    new PathStep.MoveTo(0, 0),
                    new PathStep.LineTo(10, 10),
                    new PathStep.LineTo(20, 0),
                    new PathStep.Close());
        }

        @Test
        @DisplayName("and either of fewer than two points paints nothing")
        void toofewPointsPaintNothing() {
            assertThat(drawingOf("line 5x5"))
                    .as("one point is a position rather than a shape, and "
                            + "painting a dot would invent a decision")
                    .isEmpty();
            assertThat(drawingOf("polygon 5x5")).isEmpty();
        }

        @Test
        @DisplayName("CURVE of three points is quadratic")
        void acurveOfThreeIsQuadratic() {
            assertThat(onlyDrawing("curve 0x0 50x100 100x0").path()).containsExactly(
                    new PathStep.MoveTo(0, 0),
                    new PathStep.QuadraticTo(50, 100, 100, 0));
        }

        @Test
        @DisplayName("and of four points is cubic, the same word meaning two curves")
        void acurveOfFourIsCubic() {
            assertThat(onlyDrawing("curve 0x0 30x100 70x100 100x0").path())
                    .containsExactly(
                            new PathStep.MoveTo(0, 0),
                            new PathStep.CubicTo(30, 100, 70, 100, 100, 0));
        }

        @Test
        @DisplayName("ARC with no length turns a quarter, not nothing")
        void anarcDefaultsToAQuarterTurn() {
            // `IS_NONE(arg+3) ? 90`. An arc of no length would draw nothing
            // and be indistinguishable from a command nobody wrote.
            PathStep.ArcTo arc = (PathStep.ArcTo) onlyDrawing("arc 50x50 20x20 0")
                    .path().getFirst();

            assertThat(arc.turnsThrough()).isEqualTo(90);
        }

        @Test
        @DisplayName("and CLOSED makes it a pie rather than an open sweep")
        void anarcCanBeClosed() {
            PathStep.ArcTo arc = (PathStep.ArcTo)
                    onlyDrawing("arc 50x50 20x20 0 120 closed").path().getFirst();

            assertThat(arc.turnsThrough()).isEqualTo(120);
            assertThat(arc.closes()).isTrue();
        }
    }

    @Nested
    @DisplayName("paths written by hand")
    class TheShapeSubDialect {

        @Test
        @DisplayName("SHAPE builds one path from its own block")
        void ashapeBuildsOnePath() {
            assertThat(onlyDrawing("shape [move 10x10 line 50x10 line 50x50 close]")
                    .path()).containsExactly(
                            new PathStep.MoveTo(10, 10),
                            new PathStep.LineTo(50, 10),
                            new PathStep.LineTo(50, 50),
                            new PathStep.Close());
        }

        @Test
        @DisplayName("a lit-word step is measured from where the path stands")
        void alitWordStepIsRelative() {
            // The reason losing a lit-word's mark in DELECT would have been
            // serious: every relative path would have quietly become absolute.
            assertThat(onlyDrawing("shape [move 10x10 'line 5x5]").path())
                    .containsExactly(
                            new PathStep.MoveTo(10, 10),
                            new PathStep.LineTo(15, 15));
        }

        @Test
        @DisplayName("while a plain one is measured from the surface")
        void aplainStepIsAbsolute() {
            assertThat(onlyDrawing("shape [move 10x10 line 5x5]").path())
                    .containsExactly(
                            new PathStep.MoveTo(10, 10),
                            new PathStep.LineTo(5, 5));
        }

        @Test
        @DisplayName("HLINE and VLINE take one number, keeping the other coordinate")
        void ahorizontalLineKeepsItsHeight() {
            assertThat(onlyDrawing("shape [move 10x20 hline 60 vline 70]").path())
                    .containsExactly(
                            new PathStep.MoveTo(10, 20),
                            new PathStep.LineTo(60, 20),
                            new PathStep.LineTo(60, 70));
        }

        @Test
        @DisplayName("and a curve in a shape is a curve in the path")
        void ashapeCanCurve() {
            assertThat(onlyDrawing("shape [move 0x0 qcurve 50x100 100x0]").path())
                    .containsExactly(
                            new PathStep.MoveTo(0, 0),
                            new PathStep.QuadraticTo(50, 100, 100, 0));
        }
    }

    @Nested
    @DisplayName("transforms")
    class TheTransforms {

        @Test
        @DisplayName("a block with none carries none")
        void ablockWithNoneCarriesNone() {
            assertThat(onlyDrawing("box 10x10 50x50").transform().isNone()).isTrue();
        }

        @Test
        @DisplayName("TRANSLATE moves what comes after it")
        void translateMoves() {
            assertThat(onlyDrawing("translate 10x20 box 0x0 10x10").transform())
                    .isEqualTo(Transform.movedBy(10, 20));
        }

        @Test
        @DisplayName("SCALE and ROTATE do the same, and they multiply together")
        void transformsMultiply() {
            Transform carried =
                    onlyDrawing("translate 10x0 scale 2 2 box 0x0 10x10").transform();

            assertThat(carried)
                    .as("moved then scaled, as one")
                    .isEqualTo(Transform.movedBy(10, 0)
                            .combinedWith(Transform.scaledBy(2, 2)));
        }

        @Test
        @DisplayName("RESET-MATRIX undoes every one of them")
        void resetUndoesThem() {
            assertThat(onlyDrawing("""
                    translate 10x20 scale 2 2 reset-matrix box 0x0 10x10""")
                    .transform().isNone()).isTrue();
        }

        @Test
        @DisplayName("and a shape carries the transform standing when it was reached")
        void eachShapeCarriesItsOwn() {
            List<PaintInstruction> painted =
                    drawingOf("box 0x0 10x10 translate 30x30 box 0x0 10x10");

            assertThat(((PaintInstruction.Drawn) painted.getFirst())
                    .transform().isNone()).isTrue();
            assertThat(((PaintInstruction.Drawn) painted.get(1)).transform())
                    .isEqualTo(Transform.movedBy(30, 30));
        }

        @Test
        @DisplayName("PUSH puts back both the state and the transform")
        void pushRestoresEverything() {
            List<PaintInstruction> painted = drawingOf("""
                    push [pen 255.0.0 translate 50x50 box 0x0 10x10]
                    box 20x20 30x30""");

            PaintInstruction.Drawn inside = (PaintInstruction.Drawn) painted.getFirst();
            PaintInstruction.Drawn after = (PaintInstruction.Drawn) painted.get(1);

            assertThat(inside.painted().strokeColour()).contains(new Colour(255, 0, 0));
            assertThat(inside.transform()).isEqualTo(Transform.movedBy(50, 50));
            assertThat(after.painted().strokeColour())
                    .as("what a push set is put back, so a piece of drawing composes")
                    .contains(Colour.BLACK);
            assertThat(after.transform().isNone()).isTrue();
        }
    }

    @Nested
    @DisplayName("what it does not paint")
    class TheGaps {

        @Test
        @DisplayName("a command this build does not paint is skipped")
        void anunpaintedCommandIsSkipped() {
            // The opposite of the binary dialect's call, and deliberately.
            // There an unknown code writes a message of the wrong length and
            // the far end cannot read it. Here the cost of skipping is a
            // picture missing one thing, and of refusing, a picture missing
            // everything.
            assertThat(drawingOf("grad-pen linear normal box 10x10 50x50"))
                    .as("the box still draws")
                    .hasSize(1);
        }

        @Test
        @DisplayName("and a block that will not parse paints what it managed, not nothing")
        void amalformedBlockPaintsWhatItManaged() {
            // A gob's content is not a place a script is standing, so nobody
            // is there to catch a raise. Letting one out would take the whole
            // window down for one mistyped argument.
            assertThat(drawingOf("box 10x10 50x50 grad-pen linear normal 0x0 0x100 1x1"))
                    .as("the box drew before the bad arguments were reached")
                    .hasSize(1);
        }

        @Test
        @DisplayName("and the gap is the same gap everywhere, because it is decided here")
        void thegapIsInOnePlace() {
            assertThat(drawingOf("gamma 2.2 arrow 1x1 255.0.0 box 10x10 50x50"))
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("where the drawing goes")
    class TheClipping {

        @Test
        @DisplayName("a draw block is clipped to its own gob, like any other content")
        void drawingIsClippedToItsGob() {
            assertThat(onlyDrawing("box 0x0 500x500").where().clip())
                    .isEqualTo(new ClipRectangle(0, 0, 100, 100));
        }

        @Test
        @DisplayName("and a child gob's drawing is clipped by its parent too")
        void achildsDrawingIsClippedByItsParent() {
            AGobAndItsDialect drawn = gobAndDialectFrom("""
                    parent: make gob! [size: 40x40 color: 1.1.1]
                    append parent make gob! [
                        offset: 10x10 size: 100x100 draw: [box 0x0 100x100]
                    ]
                    parent""");

            assertThat(PaintList.of(drawn.gob(), drawn.dialect())
                    .instructions().get(1).where().clip())
                    .isEqualTo(new ClipRectangle(10, 10, 30, 30));
        }
    }
}
