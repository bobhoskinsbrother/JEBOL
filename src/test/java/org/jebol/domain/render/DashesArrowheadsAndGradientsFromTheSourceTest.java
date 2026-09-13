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

class DashesArrowheadsAndGradientsFromTheSourceTest {

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
    @DisplayName("LINE-PATTERN")
    class Dashes {

        private static PaintState theDashedStrokeIn(String drawBlock) {
            List<PaintInstruction> painted = drawingOf(drawBlock);
            return ((PaintInstruction.Drawn) painted.getLast()).painted();
        }

        @Test
        @DisplayName("turns the stroke into dashes of the lengths given")
        void turnsTheStrokeIntoDashes() {
            assertThat(theDashedStrokeIn("line-pattern 255.0.0 10 4 line 0x0 90x90")
                    .dashes()).containsExactly(10.0, 4.0);
        }

        @Test
        @DisplayName("one length is equal dashes and gaps")
        void oneLengthIsEqualDashesAndGaps() {
            assertThat(theDashedStrokeIn("line-pattern 255.0.0 6 line 0x0 90x90")
                    .dashes()).containsExactly(6.0);
        }

        @Test
        @DisplayName("a shape started solid stays solid")
        void ashapeStartedSolidStaysSolid() {
            assertThat(onlyDrawing("line 0x0 90x90").painted().dashes()).isEmpty();
        }

        @Test
        @DisplayName("and with no lengths at all it is a solid line again")
        void withNoLengthsItIsSolidAgain() {
            List<PaintInstruction> painted = drawingOf(
                    "line-pattern 255.0.0 10 4 line 0x0 90x90 "
                            + "line-pattern line 0x0 90x10");

            assertThat(painted)
                    .as("the gaps, the dashed line, and then a plain line")
                    .hasSize(3);
            assertThat(((PaintInstruction.Drawn) painted.get(1)).painted().dashes())
                    .containsExactly(10.0, 4.0);
            assertThat(((PaintInstruction.Drawn) painted.get(2)).painted().dashes())
                    .isEmpty();
        }

        @Test
        @DisplayName("PUSH puts the dashes back with everything else")
        void pushPutsTheDashesBack() {
            List<PaintInstruction> painted = drawingOf(
                    "push [line-pattern 255.0.0 8 line 0x0 90x90] line 0x0 90x10");

            assertThat(((PaintInstruction.Drawn) painted.get(1)).painted().dashes())
                    .containsExactly(8.0);
            assertThat(((PaintInstruction.Drawn) painted.getLast()).painted().dashes())
                    .as("outside the push the dashes are gone")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("ARROW")
    class Arrowheads {

        @Test
        @DisplayName("puts a head on the far end of a line, as a shape of its own")
        void putsAHeadOnTheFarEnd() {
            List<PaintInstruction> painted =
                    drawingOf("arrow 255.0.0 0x1 line 10x10 90x10");

            assertThat(painted).hasSize(2);
            PaintInstruction.Drawn head = (PaintInstruction.Drawn) painted.get(1);
            assertThat(head.path()).hasSize(4);
            assertThat(head.path().getLast().kind()).isEqualTo(PathStepKind.CLOSE);
        }

        @Test
        @DisplayName("and the head is filled in the colour the command named")
        void theHeadIsFilledInItsOwnColour() {
            PaintInstruction.Drawn head = (PaintInstruction.Drawn)
                    drawingOf("pen 0.0.255 arrow 255.0.0 0x1 line 10x10 90x10").get(1);

            assertThat(head.painted().fillColour()).contains(new Colour(255, 0, 0));
            assertThat(head.painted().strokeColour()).isEmpty();
        }

        @Test
        @DisplayName("with no colour of its own it takes the pen's")
        void withNoColourOfItsOwnItTakesThePens() {
            PaintInstruction.Drawn head = (PaintInstruction.Drawn)
                    drawingOf("pen 0.0.255 arrow 0x1 line 10x10 90x10").get(1);

            assertThat(head.painted().fillColour()).contains(new Colour(0, 0, 255));
        }

        @Test
        @DisplayName("and on the near end when the first flag says so")
        void andOnTheNearEndToo() {
            int withFarHead = ((PaintInstruction.Drawn)
                    drawingOf("arrow 255.0.0 0x1 line 10x10 90x10").get(1)).path().size();
            int withBothHeads = ((PaintInstruction.Drawn)
                    drawingOf("arrow 255.0.0 1x1 line 10x10 90x10").get(1)).path().size();

            assertThat(withBothHeads).isGreaterThan(withFarHead);
        }

        @Test
        @DisplayName("with both flags zero it is the line it always was")
        void withBothFlagsZeroItIsJustALine() {
            assertThat(drawingOf("arrow 255.0.0 0x0 line 10x10 90x10")).hasSize(1);
            assertThat(onlyDrawing("arrow 255.0.0 0x0 line 10x10 90x10").path())
                    .isEqualTo(onlyDrawing("line 10x10 90x10").path());
        }

        @Test
        @DisplayName("and the gaps between dashes are drawn in the dash colour")
        void thegapsBetweenDashesAreDrawnInTheDashColour() {
            List<PaintInstruction> painted = drawingOf(
                    "pen 0.0.255 line-pattern 255.0.0 10 6 line 10x50 90x50");

            assertThat(painted).hasSize(2);
            PaintInstruction.Drawn gaps = (PaintInstruction.Drawn) painted.getFirst();
            assertThat(gaps.painted().strokeColour()).contains(new Colour(255, 0, 0));
            assertThat(gaps.painted().dashes())
                    .as("the gaps are a plain path, not a dashed one")
                    .isEmpty();
            assertThat(gaps.path()).hasSizeGreaterThan(2);
        }

        @Test
        @DisplayName("a head is four line widths long, so a heavier line carries a bigger one")
        void aheadIsSizedFromTheLineWidth() {
            List<PathStep> thin = ((PaintInstruction.Drawn) drawingOf(
                    "line-width 1 arrow 255.0.0 0x1 line 10x50 90x50").get(1)).path();
            List<PathStep> thick = ((PaintInstruction.Drawn) drawingOf(
                    "line-width 4 arrow 255.0.0 0x1 line 10x50 90x50").get(1)).path();

            assertThat(headWidthOf(thin)).isCloseTo(4, within(0.5));
            assertThat(headWidthOf(thick)).isCloseTo(16, within(0.5));
        }

        private double headWidthOf(List<PathStep> path) {
            double lowest = Double.MAX_VALUE;
            double highest = -Double.MAX_VALUE;
            for (PathStep step : path) {
                if (step instanceof PathStep.LineTo drawn) {
                    lowest = Math.min(lowest, drawn.down());
                    highest = Math.max(highest, drawn.down());
                }
            }
            return highest - lowest;
        }

        @Test
        @DisplayName("and a closed shape gets no head, having no ends")
        void aclosedShapeGetsNoHead() {
            assertThat(drawingOf("arrow 255.0.0 1x1 box 10x10 90x90")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("GRAD-PEN")
    class Gradients {

        @Test
        @DisplayName("a linear gradient fills with the colours it was given")
        void alinearGradientFillsWithItsColours() {
            PaintState filled = onlyDrawing(
                    "grad-pen linear normal 50x50 0 100 0 1 1 [255.0.0 0.0.255] "
                            + "box 0x0 100x100").painted();

            assertThat(filled.fillGradient()).isPresent();
            assertThat(filled.fillGradient().orElseThrow().colours())
                    .containsExactly(new Colour(255, 0, 0), new Colour(0, 0, 255));
            assertThat(filled.fillGradient().orElseThrow().radial()).isFalse();
        }

        @Test
        @DisplayName("and a radial one runs out from its offset")
        void aradialGradientRunsOutFromItsOffset() {
            Gradient spread = onlyDrawing(
                    "grad-pen radial normal 40x60 0 100 0 1 1 [255.0.0 0.0.255] "
                            + "box 0x0 100x100").painted().fillGradient().orElseThrow();

            assertThat(spread.radial()).isTrue();
            assertThat(spread.acrossOffset()).isCloseTo(40, within(CLOSE_ENOUGH));
            assertThat(spread.downOffset()).isCloseTo(60, within(CLOSE_ENOUGH));
        }

        @Test
        @DisplayName("a conic gradient is drawn as a fan of wedges, worked out here")
        void aconicGradientIsAFanOfWedges() {
            List<PaintInstruction> painted = drawingOf(
                    "pen off grad-pen conic normal 50x50 0 100 0 1 1 "
                            + "[255.0.0 0.0.255] box 0x0 100x100");

            assertThat(painted).hasSizeGreaterThan(100);
            assertThat(painted).allSatisfy(piece -> assertThat(
                    ((PaintInstruction.Drawn) piece).painted().fillGradient())
                    .as("a renderer is handed flat shapes, never a conic gradient")
                    .isEmpty());
        }

        @Test
        @DisplayName("and a diamond one as rings stood on their corners")
        void adiamondGradientIsRings() {
            List<PaintInstruction> painted = drawingOf(
                    "pen off grad-pen diamond normal 50x50 0 100 0 1 1 "
                            + "[255.0.0 0.0.255] box 0x0 100x100");

            assertThat(painted).hasSizeGreaterThan(50);
            PaintInstruction.Drawn first = (PaintInstruction.Drawn) painted.getFirst();
            assertThat(first.path()).hasSize(5);
        }

        @Test
        @DisplayName("a diagonal gradient is a linear one turned a further forty-five")
        void adiagonalGradientIsLinearTurned() {
            Gradient straight = onlyDrawing(
                    "grad-pen linear normal 50x50 0 100 0 1 1 [255.0.0 0.0.255] "
                            + "box 0x0 100x100").painted().fillGradient().orElseThrow();
            Gradient diagonal = onlyDrawing(
                    "grad-pen diagonal normal 50x50 0 100 0 1 1 [255.0.0 0.0.255] "
                            + "box 0x0 100x100").painted().fillGradient().orElseThrow();

            assertThat(diagonal.angle()).isEqualTo(straight.angle() + 45);
            assertThat(diagonal.shape().aToolkitCanDrawIt()).isTrue();
        }

        @Test
        @DisplayName("and a cubic one eases its colours towards the middle")
        void acubicGradientEasesItsColours() {
            Gradient eased = onlyDrawing(
                    "grad-pen cubic normal 50x50 0 100 0 1 1 "
                            + "[255.0.0 0.255.0 0.0.255] box 0x0 100x100")
                    .painted().fillGradient().orElseThrow();

            assertThat(eased.stops().get(1))
                    .as("the middle colour still sits in the middle")
                    .isEqualTo(0.5);
            Gradient evenly = onlyDrawing(
                    "grad-pen linear normal 50x50 0 100 0 1 1 "
                            + "[255.0.0 0.255.0 0.0.255 255.255.0] box 0x0 100x100")
                    .painted().fillGradient().orElseThrow();
            Gradient cubic = onlyDrawing(
                    "grad-pen cubic normal 50x50 0 100 0 1 1 "
                            + "[255.0.0 0.255.0 0.0.255 255.255.0] box 0x0 100x100")
                    .painted().fillGradient().orElseThrow();
            assertThat(cubic.stops().get(1)).isLessThan(evenly.stops().get(1));
        }

        @Test
        @DisplayName("and one colour is no gradient at all")
        void onecolourIsNoGradient() {
            assertThat(onlyDrawing(
                    "fill-pen 1.2.3 grad-pen linear normal 50x50 0 100 0 1 1 "
                            + "[255.0.0] box 0x0 100x100")
                    .painted().fillGradient()).isEmpty();
        }

        @Test
        @DisplayName("a shape with a gradient and no fill colour still paints")
        void ashapeWithAGradientAndNoFillColourStillPaints() {
            assertThat(drawingOf(
                    "pen off grad-pen linear normal 50x50 0 100 0 1 1 "
                            + "[255.0.0 0.0.255] box 0x0 100x100")).hasSize(1);
        }
    }
}
