package org.jebol.adapter.host;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import org.jebol.application.Bounds;
import org.jebol.application.Interpreter;
import org.jebol.domain.eval.ScreenMetric;
import org.jebol.domain.eval.ScreenPort;
import org.jebol.domain.host.HostService;
import org.jebol.domain.value.GobValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The native renderer: Swing for the windows, Java2D for the painting.
 *
 * <p>The suite runs with {@code java.awt.headless=true}, so on every machine
 * this ever runs on the adapter has no display. That would leave the whole
 * class untested except for its refusals, and it does not, because the
 * painting and the windowing are separate things. Java2D draws onto a
 * {@link BufferedImage} with no display at all, so what a gob paints can be
 * asserted pixel by pixel here; only opening a real window cannot be.
 *
 * <p>What that leaves untested in the gate is named rather than hidden: no
 * test here opens a window, packs a frame or reads a real screen's size.
 * Those need a display and are exercised by hand.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
class DesktopScreenFromTheSourceTest {

    private static GobValue gobFrom(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.defineFreshWordsIn(source);
        return (GobValue) interpreter.run(source).value();
    }

    /** What a gob paints, read back as pixels. */
    private static BufferedImage painted(GobValue gob, int wide, int high) {
        BufferedImage surface =
                new BufferedImage(wide, high, BufferedImage.TYPE_INT_ARGB);
        Graphics2D onto = surface.createGraphics();
        try {
            DesktopPainting.paint(onto, gob);
        } finally {
            onto.dispose();
        }
        return surface;
    }

    private static Color colourAt(BufferedImage surface, int across, int down) {
        return new Color(surface.getRGB(across, down), true);
    }

    @Nested
    @DisplayName("what a gob paints")
    class ThePainting {

        @Test
        @DisplayName("a colour gob fills its whole area with that colour")
        void aColourFillsItsArea() {
            BufferedImage surface = painted(
                    gobFrom("make gob! [size: 40x30 color: 200.100.50]"), 60, 50);

            assertThat(colourAt(surface, 0, 0)).isEqualTo(new Color(200, 100, 50));
            assertThat(colourAt(surface, 39, 29)).isEqualTo(new Color(200, 100, 50));
        }

        @Test
        @DisplayName("and nothing outside it, because a gob is clipped to its size")
        void itPaintsNothingOutsideItself() {
            BufferedImage surface = painted(
                    gobFrom("make gob! [size: 40x30 color: 200.100.50]"), 60, 50);

            assertThat(colourAt(surface, 40, 0).getAlpha())
                    .as("one pixel past the right edge")
                    .isZero();
            assertThat(colourAt(surface, 0, 30).getAlpha())
                    .as("one pixel past the bottom edge")
                    .isZero();
        }

        @Test
        @DisplayName("a gob of no size paints nothing at all")
        void nothingIsPaintedForNoSize() {
            BufferedImage surface = painted(
                    gobFrom("make gob! [size: 0x0 color: 255.0.0]"), 10, 10);

            assertThat(colourAt(surface, 0, 0).getAlpha()).isZero();
        }

        @Test
        @DisplayName("the fourth octet is opacity, running the same way Java's alpha does")
        void theFourthOctetIsOpacity() {
            // Worth pinning because the guess goes the other way. The C
            // settles it where it decides whether a gob can be painted over:
            // `if (VAL_TUPLE_LEN(val) < 4 || VAL_TUPLE(val)[3] == 255)
            // SET_GOB_OPAQUE(gob);`.
            BufferedImage opaque = painted(
                    gobFrom("make gob! [size: 10x10 color: 255.0.0.255]"), 10, 10);
            BufferedImage invisible = painted(
                    gobFrom("make gob! [size: 10x10 color: 255.0.0.0]"), 10, 10);

            assertThat(colourAt(opaque, 5, 5).getAlpha()).isEqualTo(255);
            assertThat(colourAt(invisible, 5, 5).getAlpha()).isZero();
        }

        @Test
        @DisplayName("and three octets mean opaque, because a fourth of 255 is filled in")
        void threeOctetsMeanOpaque() {
            assertThat(colourAt(painted(
                    gobFrom("make gob! [size: 10x10 color: 0.128.0]"), 10, 10), 5, 5))
                    .isEqualTo(new Color(0, 128, 0, 255));
        }

        @Test
        @DisplayName("a child paints over its parent, at the child's own offset")
        void achildPaintsOverItsParent() {
            BufferedImage surface = painted(gobFrom("""
                    parent: make gob! [size: 40x40 color: 0.0.255]
                    append parent make gob! [offset: 10x10 size: 10x10 color: 255.255.0]
                    parent"""), 40, 40);

            assertThat(colourAt(surface, 5, 5))
                    .as("outside the child, the parent shows")
                    .isEqualTo(new Color(0, 0, 255));
            assertThat(colourAt(surface, 15, 15))
                    .as("inside it, the child does")
                    .isEqualTo(new Color(255, 255, 0));
            assertThat(colourAt(surface, 25, 25))
                    .as("and past the child again, the parent")
                    .isEqualTo(new Color(0, 0, 255));
        }

        @Test
        @DisplayName("and a child is clipped to its parent, however big it says it is")
        void achildIsClippedToItsParent() {
            BufferedImage surface = painted(gobFrom("""
                    parent: make gob! [size: 20x20 color: 0.0.255]
                    append parent make gob! [offset: 10x10 size: 100x100 color: 255.255.0]
                    parent"""), 60, 60);

            assertThat(colourAt(surface, 15, 15)).isEqualTo(new Color(255, 255, 0));
            assertThat(colourAt(surface, 25, 25).getAlpha())
                    .as("the child claimed a hundred pixels and the parent has twenty")
                    .isZero();
        }

        @Test
        @DisplayName("a gob's alpha makes what it paints see-through")
        void alphaMakesItSeeThrough() {
            BufferedImage surface = painted(gobFrom("""
                    parent: make gob! [size: 20x20 color: 0.0.0]
                    append parent make gob! [size: 20x20 color: 255.255.255 alpha: 128]
                    parent"""), 20, 20);

            Color blended = colourAt(surface, 10, 10);
            assertThat(blended.getRed())
                    .as("half white over black is a grey, not either of them")
                    .isBetween(100, 155);
        }

        @Test
        @DisplayName("a draw block paints nothing yet, and that is a named gap")
        void adrawBlockPaintsNothing() {
            // Not an oversight. The DRAW dialect is thirty commands from
            // boot/draw.reb and a separate piece of work; spec/screen.allium
            // says so and asks whether it should refuse instead of showing a
            // blank window.
            BufferedImage surface = painted(
                    gobFrom("make gob! [size: 20x20 draw: [pen red line 0x0 20x20]]"),
                    20, 20);

            assertThat(colourAt(surface, 10, 10).getAlpha()).isZero();
        }
    }

    @Nested
    @DisplayName("with no display, which is every machine the suite runs on")
    class TheHeadlessMachine {

        @Test
        @DisplayName("the suite really is headless, or the rest of this proves nothing")
        void theSuiteIsHeadless() {
            assertThat(GraphicsEnvironment.isHeadless())
                    .as("build.gradle.kts sets java.awt.headless=true, and these "
                            + "tests exist to exercise the refusal path")
                    .isTrue();
        }

        @Test
        @DisplayName("the adapter says it has no display")
        void itHasNoDisplay() {
            assertThat(DesktopScreen.onThisMachine().hasADisplay()).isFalse();
        }

        @Test
        @DisplayName("every measurement is zero rather than a guess")
        void everyMeasurementIsZero() {
            DesktopScreen screen = DesktopScreen.onThisMachine();

            for (ScreenMetric metric : ScreenMetric.values()) {
                if (metric.isACount()) {
                    continue;
                }
                assertThat(screen.measure(metric, 0))
                        .as("%s", metric.spelling())
                        .isEqualTo(org.jebol.domain.value.PairValue.of(0, 0));
            }
        }

        @Test
        @DisplayName("and there are no displays to count")
        void thereAreNoDisplays() {
            assertThat(DesktopScreen.onThisMachine().displayCount()).isZero();
        }

        @Test
        @DisplayName("taking the root gob is accepted, so the library still loads")
        void takingTheRootIsAccepted() {
            DesktopScreen screen = DesktopScreen.onThisMachine();

            ScreenPort.takeAsTheRoot(screen, GobValue.empty());

            assertThat(screen.takeQueuedEvents()).isEmpty();
        }

        @Test
        @DisplayName("but showing refuses, because that is the one that needs pixels")
        void showingRefuses() {
            DesktopScreen screen = DesktopScreen.onThisMachine();

            assertThatThrownBy(() -> screen.show(GobValue.empty()))
                    .isInstanceOf(ScreenPort.Denied.class)
                    .hasMessageContaining("no display");
        }

        @Test
        @DisplayName("and an interpreter given this screen refuses to view, by that name")
        void anInterpreterRefusesToView() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));
            interpreter.useScreen(DesktopScreen.onThisMachine());
            String script = """
                    e: try [view/no-wait make gob! [size: 100x100]]
                    either error? e [form e/arg1] ["no error at all"]""";
            interpreter.defineFreshWordsIn(script);

            assertThat(interpreter.display(interpreter.run(script)))
                    .contains("not present");
        }

        @Test
        @DisplayName("and the root gob it is given is sized at nothing")
        void theRootIsSizedAtNothing() {
            Interpreter interpreter = Interpreter.withBounds(
                    Bounds.standard().granting(HostService.WINDOWS));
            interpreter.useScreen(DesktopScreen.onThisMachine());

            assertThat(interpreter.display(interpreter.run(
                    "system/view/screen-gob/size"))).isEqualTo("0x0");
        }
    }
}
