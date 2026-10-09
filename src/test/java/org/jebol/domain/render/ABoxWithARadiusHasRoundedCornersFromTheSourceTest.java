package org.jebol.domain.render;

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

class ABoxWithARadiusHasRoundedCornersFromTheSourceTest {

    private final Interpreter interpreter = Interpreter.withBounds(
            Bounds.standard().granting(HostService.WINDOWS));

    private List<PathStep> thePathOf(String box) {
        String source = "make gob! [size: 100x100 draw: [" + box + "]]";
        interpreter.defineFreshWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        ObjectValue dialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        List<PaintInstruction> painted = PaintList.of(gob, dialect).instructions();
        assertThat(painted).hasSize(1);
        return ((PaintInstruction.Drawn) painted.getFirst()).path();
    }

    private long curvesIn(List<PathStep> path) {
        return path.stream().filter(PathStep.CubicTo.class::isInstance).count();
    }

    @Test
    @DisplayName("a box with no radius is four straight sides")
    void noRadiusIsSquare() {
        assertThat(thePathOf("box 10x10 50x30")).containsExactly(
                new PathStep.MoveTo(10, 10),
                new PathStep.LineTo(50, 10),
                new PathStep.LineTo(50, 30),
                new PathStep.LineTo(10, 30),
                new PathStep.Close());
    }

    @ParameterizedTest(name = "a radius of {0} is square corners")
    @ValueSource(strings = {"0", "-3", "0.0"})
    @DisplayName("a radius of nothing or less is square corners")
    void aRadiusOfNothingIsSquare(String radius) {
        assertThat(curvesIn(thePathOf("box 10x10 50x30 " + radius))).isZero();
    }

    @Test
    @DisplayName("a radius rounds all four corners, each a quarter circle")
    void aRadiusRoundsEveryCorner() {
        List<PathStep> path = thePathOf("box 10x10 50x30 5");

        assertThat(curvesIn(path)).isEqualTo(4);
        assertThat(path.getFirst()).isEqualTo(new PathStep.MoveTo(15, 10));
        assertThat(path.getLast()).isEqualTo(new PathStep.Close());
    }

    @Test
    @DisplayName("the straight sides stop a radius short of each corner")
    void theSidesStopShort() {
        List<PathStep> path = thePathOf("box 10x10 50x30 5");

        assertThat(path).contains(
                new PathStep.LineTo(45, 10),
                new PathStep.LineTo(50, 25),
                new PathStep.LineTo(15, 30),
                new PathStep.LineTo(10, 15));
    }

    @Test
    @DisplayName("each curve ends on the next side, so the corner at the top right ends 5 down the right")
    void eachCurveEndsOnTheNextSide() {
        List<PathStep> path = thePathOf("box 10x10 50x30 5");
        PathStep.CubicTo theTopRight = (PathStep.CubicTo) path.get(2);

        assertThat(theTopRight.across()).isEqualTo(50);
        assertThat(theTopRight.down()).isEqualTo(15);
    }

    @Test
    @DisplayName("a radius more than half the shorter side is held to half of it, as AGG's rounded box does")
    void aBigRadiusIsHeldToHalf() {
        List<PathStep> path = thePathOf("box 10x10 50x30 50");

        assertThat(path.getFirst()).isEqualTo(new PathStep.MoveTo(20, 10));
        assertThat(curvesIn(path)).isEqualTo(4);
    }

    @Test
    @DisplayName("a radius of exactly half the shorter side makes round ends")
    void halfTheShorterSideIsRoundEnds() {
        assertThat(thePathOf("box 10x10 50x30 10").getFirst()).isEqualTo(new PathStep.MoveTo(20, 10));
    }

    @Test
    @DisplayName("the scroller's track, box 0x0 21x21 3, has corners of 3")
    void theScrollerTrack() {
        assertThat(thePathOf("box 0x0 21x21 3").getFirst()).isEqualTo(new PathStep.MoveTo(3, 0));
    }

    @Test
    @DisplayName("corners given the other way round make the same box")
    void cornersInEitherOrder() {
        assertThat(thePathOf("box 50x30 10x10 5")).isEqualTo(thePathOf("box 10x10 50x30 5"));
    }

    @ParameterizedTest(name = "a radius of {0} is square corners")
    @ValueSource(strings = {"{5}", "5x5", "#(true)"})
    @DisplayName("a radius that is no number is square corners")
    void aRadiusThatIsNoNumberIsSquare(String radius) {
        assertThat(curvesIn(thePathOf("box 10x10 50x30 " + radius))).isZero();
    }
}
