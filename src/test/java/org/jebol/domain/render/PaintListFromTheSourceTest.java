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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * One gob tree, walked once, as the thing every renderer is handed.
 *
 * <p>This file is where the claim "the desktop and the browser show the same
 * picture" is either true or not. It is not a promise checked afterwards by
 * comparing screenshots: the list is the single input to every renderer, so
 * two renderers cannot disagree about where a rectangle goes, because neither
 * of them works it out.
 *
 * <p>What is left for comparing pictures, and is not tested here: glyph
 * shapes and anti-aliased edges. No two rasterisers agree on those and no
 * amount of shared input fixes it. Geometry and colour need no tolerance;
 * text does.
 *
 * <p>Specified in {@code spec/screen.allium}.
 */
class PaintListFromTheSourceTest {

    private static GobValue gobFrom(String source) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        interpreter.defineFreshWordsIn(source);
        return (GobValue) interpreter.run(source).value();
    }

    private static List<PaintInstruction> listFor(String source) {
        return PaintList.of(gobFrom(source)).instructions();
    }

    private static PaintInstruction.Fill fillAt(List<PaintInstruction> list, int at) {
        return (PaintInstruction.Fill) list.get(at);
    }

    @Nested
    @DisplayName("the order things are painted in")
    class TheOrder {

        @Test
        @DisplayName("a gob comes before its children, which is what in front means")
        void aParentComesFirst() {
            List<PaintInstruction> list = listFor("""
                    parent: make gob! [size: 40x40 color: 1.1.1]
                    append parent make gob! [size: 10x10 color: 2.2.2]
                    parent""");

            assertThat(fillAt(list, 0).colour()).isEqualTo(new Colour(1, 1, 1));
            assertThat(fillAt(list, 1).colour()).isEqualTo(new Colour(2, 2, 2));
        }

        @Test
        @DisplayName("and children come in the order the pane holds them")
        void childrenComeInPaneOrder() {
            List<PaintInstruction> list = listFor("""
                    parent: make gob! [size: 40x40 color: 1.1.1]
                    append parent make gob! [size: 10x10 color: 2.2.2]
                    append parent make gob! [size: 10x10 color: 3.3.3]
                    parent""");

            assertThat(list.stream().map(each ->
                    ((PaintInstruction.Fill) each).colour().red()))
                    .containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("a whole branch is painted before the next branch starts")
        void abranchFinishesBeforeTheNext() {
            // Depth first, not breadth first. Breadth first would put a
            // grandchild behind an uncle, which is a picture with the wrong
            // thing on top and reads as a renderer bug.
            List<PaintInstruction> list = listFor("""
                    parent: make gob! [size: 60x60 color: 1.1.1]
                    first-branch: make gob! [size: 20x20 color: 2.2.2]
                    append first-branch make gob! [size: 5x5 color: 3.3.3]
                    append parent first-branch
                    append parent make gob! [size: 20x20 color: 4.4.4]
                    parent""");

            assertThat(list.stream().map(each ->
                    ((PaintInstruction.Fill) each).colour().red()))
                    .containsExactly(1, 2, 3, 4);
        }
    }

    @Nested
    @DisplayName("where things go")
    class ThePositions {

        @Test
        @DisplayName("a root sits at the origin whatever offset it carries")
        void arootSitsAtTheOrigin() {
            // A root's own offset is where a window goes on the screen, not
            // where its contents go inside it, so it does not shift what it
            // paints.
            assertThat(fillAt(listFor(
                    "make gob! [offset: 300x200 size: 40x40 color: 1.1.1]"), 0)
                    .where().across()).isZero();
        }

        @Test
        @DisplayName("a child sits at its own offset, measured from the surface")
        void achildSitsAtItsOffset() {
            Placement where = fillAt(listFor("""
                    parent: make gob! [size: 40x40 color: 1.1.1]
                    append parent make gob! [offset: 7x9 size: 10x10 color: 2.2.2]
                    parent"""), 1).where();

            assertThat(where.across()).isEqualTo(7);
            assertThat(where.down()).isEqualTo(9);
        }

        @Test
        @DisplayName("and a grandchild's offsets are added up, not left to a renderer")
        void agrandchildsOffsetsAreAddedUp() {
            Placement where = fillAt(listFor("""
                    parent: make gob! [size: 60x60 color: 1.1.1]
                    child: make gob! [offset: 10x10 size: 40x40 color: 2.2.2]
                    append child make gob! [offset: 5x3 size: 10x10 color: 3.3.3]
                    append parent child
                    parent"""), 2).where();

            assertThat(where.across())
                    .as("ten from the child plus five from the grandchild")
                    .isEqualTo(15);
            assertThat(where.down()).isEqualTo(13);
        }

        @Test
        @DisplayName("a fractional offset lands on a whole pixel")
        void afractionalOffsetIsRounded() {
            // A gob's offsets and sizes are float pixels and a surface has
            // whole ones. Rounded once here rather than by each renderer,
            // because two renderers that rounded differently would be a
            // pixel apart and nothing would say which was right.
            Placement where = fillAt(listFor("""
                    parent: make gob! [size: 40x40 color: 1.1.1]
                    append parent make gob! [offset: 7.6x9.4 size: 10x10 color: 2.2.2]
                    parent"""), 1).where();

            assertThat(where.across()).isEqualTo(8);
            assertThat(where.down()).isEqualTo(9);
        }
    }

    @Nested
    @DisplayName("what may be painted over")
    class TheClipping {

        @Test
        @DisplayName("a root may paint its whole self")
        void arootMayPaintItself() {
            assertThat(fillAt(listFor("make gob! [size: 40x30 color: 1.1.1]"), 0)
                    .where().clip())
                    .isEqualTo(new ClipRectangle(0, 0, 40, 30));
        }

        @Test
        @DisplayName("a child is clipped to its parent, however big it says it is")
        void achildIsClippedToItsParent() {
            assertThat(fillAt(listFor("""
                    parent: make gob! [size: 20x20 color: 1.1.1]
                    append parent make gob! [offset: 10x10 size: 100x100 color: 2.2.2]
                    parent"""), 1).where().clip())
                    .as("ten across to the parent's edge, and no further")
                    .isEqualTo(new ClipRectangle(10, 10, 10, 10));
        }

        @Test
        @DisplayName("and a grandchild is clipped by every parent above it")
        void agrandchildIsClippedByEveryParent() {
            assertThat(fillAt(listFor("""
                    parent: make gob! [size: 30x30 color: 1.1.1]
                    child: make gob! [offset: 0x0 size: 20x20 color: 2.2.2]
                    append child make gob! [offset: 0x0 size: 100x100 color: 3.3.3]
                    append parent child
                    parent"""), 2).where().clip())
                    .as("the tightest of the three wins, not the nearest")
                    .isEqualTo(new ClipRectangle(0, 0, 20, 20));
        }

        @Test
        @DisplayName("a child entirely outside its parent paints nothing at all")
        void achildOutsideItsParentPaintsNothing() {
            assertThat(listFor("""
                    parent: make gob! [size: 20x20 color: 1.1.1]
                    append parent make gob! [offset: 50x50 size: 10x10 color: 2.2.2]
                    parent""")).hasSize(1);
        }

        @Test
        @DisplayName("and neither do its own children, which are outside too")
        void norDoItsChildren() {
            assertThat(listFor("""
                    parent: make gob! [size: 20x20 color: 1.1.1]
                    away: make gob! [offset: 50x50 size: 10x10 color: 2.2.2]
                    append away make gob! [size: 5x5 color: 3.3.3]
                    append parent away
                    parent""")).hasSize(1);
        }

        @Test
        @DisplayName("a child touching its parent's edge is clipped to nothing")
        void touchingTheEdgeIsNothing() {
            // The off point. Twenty across on a parent twenty wide starts
            // exactly where the parent ends, so the overlap has no width.
            assertThat(listFor("""
                    parent: make gob! [size: 20x20 color: 1.1.1]
                    append parent make gob! [offset: 20x0 size: 10x10 color: 2.2.2]
                    parent""")).hasSize(1);
        }

        @Test
        @DisplayName("and one pixel inside it is not")
        void onePixelInsideShows() {
            assertThat(listFor("""
                    parent: make gob! [size: 20x20 color: 1.1.1]
                    append parent make gob! [offset: 19x0 size: 10x10 color: 2.2.2]
                    parent""")).hasSize(2);
        }
    }

    @Nested
    @DisplayName("a gob with no area")
    class TheEmptyGobs {

        @ParameterizedTest
        @CsvSource({"0x0", "0x40", "40x0"})
        @DisplayName("paints nothing, whichever side is missing")
        void nothingIsPaintedForNoArea(String size) {
            assertThat(listFor(
                    "make gob! [size: " + size + " color: 1.1.1]")).isEmpty();
        }

        @Test
        @DisplayName("and nothing under it is painted either")
        void nothingUnderItIsPainted() {
            assertThat(listFor("""
                    parent: make gob! [size: 0x0 color: 1.1.1]
                    append parent make gob! [size: 10x10 color: 2.2.2]
                    parent"""))
                    .as("its children would be clipped to nothing anyway, so "
                            + "walking them makes work every renderer would "
                            + "have to discover was pointless")
                    .isEmpty();
        }

        @Test
        @DisplayName("but one pixel of area is area")
        void onePixelIsArea() {
            assertThat(listFor("make gob! [size: 1x1 color: 1.1.1]")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("how much shows through")
    class TheOpacity {

        @Test
        @DisplayName("a gob with no alpha named is opaque")
        void thedefaultIsOpaque() {
            assertThat(fillAt(listFor("make gob! [size: 10x10 color: 1.1.1]"), 0)
                    .where().opacity()).isEqualTo(255);
        }

        @Test
        @DisplayName("a gob's own alpha shows on what it paints")
        void itsOwnAlphaShows() {
            assertThat(fillAt(listFor(
                    "make gob! [size: 10x10 color: 1.1.1 alpha: 128]"), 0)
                    .where().opacity()).isEqualTo(128);
        }

        @Test
        @DisplayName("and a parent's multiplies with it, so half inside half is a quarter")
        void alphaMultipliesDownTheTree() {
            assertThat(fillAt(listFor("""
                    parent: make gob! [size: 40x40 color: 1.1.1 alpha: 128]
                    append parent make gob! [size: 10x10 color: 2.2.2 alpha: 128]
                    parent"""), 1).where().opacity())
                    .as("128 of 255 twice over")
                    .isEqualTo(64);
        }

        @Test
        @DisplayName("an invisible parent makes its children invisible too")
        void aninvisibleParentHidesItsChildren() {
            assertThat(fillAt(listFor("""
                    parent: make gob! [size: 40x40 color: 1.1.1 alpha: 0]
                    append parent make gob! [size: 10x10 color: 2.2.2]
                    parent"""), 1).where().opacity()).isZero();
        }

        @Test
        @DisplayName("the colour's own fourth octet multiplies in as well")
        void thecolourOpacityMultipliesToo() {
            // A gob carries two opacities and both apply: its alpha, and the
            // fourth octet of its colour. Combining them here is what stops
            // one renderer applying both and another applying one.
            assertThat(fillAt(listFor(
                    "make gob! [size: 10x10 color: 1.1.1.128 alpha: 128]"), 0)
                    .where().opacity()).isEqualTo(64);
        }

        @Test
        @DisplayName("and three octets of colour mean the colour is opaque")
        void threeOctetsAreOpaque() {
            assertThat(fillAt(listFor("make gob! [size: 10x10 color: 1.1.1]"), 0)
                    .where().opacity()).isEqualTo(255);
        }
    }

    @Nested
    @DisplayName("what each content kind becomes")
    class TheKinds {

        @Test
        @DisplayName("a colour becomes a fill of that colour")
        void acolourBecomesAFill() {
            PaintInstruction only = listFor(
                    "make gob! [size: 10x10 color: 200.100.50]").getFirst();

            assertThat(only.kind()).isEqualTo(PaintKind.FILL);
            assertThat(((PaintInstruction.Fill) only).colour())
                    .isEqualTo(new Colour(200, 100, 50));
        }

        @Test
        @DisplayName("a string becomes writing")
        void astringBecomesWriting() {
            PaintInstruction only = listFor("""
                    make gob! [size: 100x20 text: "hello"]""").getFirst();

            assertThat(only.kind()).isEqualTo(PaintKind.WRITING);
            assertThat(((PaintInstruction.Writing) only).text()).isEqualTo("hello");
        }

        @Test
        @DisplayName("an image becomes a picture")
        void animageBecomesAPicture() {
            // The spec block of a MAKE is not evaluated, so the image has to
            // be made first and named. Writing `image: make image! 4x4` inside
            // it puts the MAKE native in the field.
            assertThat(listFor("""
                    picture: make image! 4x4
                    make gob! [size: 10x10 image: picture]""")
                    .getFirst().kind()).isEqualTo(PaintKind.PICTURE);
        }

        @Test
        @DisplayName("an empty string paints nothing rather than an empty box")
        void anemptyStringPaintsNothing() {
            assertThat(listFor("""
                    make gob! [size: 100x20 text: ""]""")).isEmpty();
        }

        @Test
        @DisplayName("a gob with no content paints nothing but still holds its children")
        void agobWithNoContentStillHoldsChildren() {
            assertThat(listFor("""
                    parent: make gob! [size: 40x40]
                    append parent make gob! [size: 10x10 color: 2.2.2]
                    parent"""))
                    .as("an invisible container is how anybody groups things")
                    .hasSize(1);
        }

        @Test
        @DisplayName("a draw block paints nothing yet, and the gap is here rather than three times")
        void adrawBlockPaintsNothing() {
            // Named rather than hidden. The DRAW dialect is thirty commands
            // from boot/draw.reb, and when it lands it adds instruction kinds
            // here -- once -- instead of a walk in each of three renderers.
            assertThat(listFor("""
                    make gob! [size: 20x20 draw: [pen red line 0x0 20x20]]"""))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("the list itself")
    class TheList {

        @Test
        @DisplayName("counts what it holds")
        void itCountsWhatItHolds() {
            assertThat(PaintList.of(gobFrom("""
                    parent: make gob! [size: 40x40 color: 1.1.1]
                    append parent make gob! [size: 10x10 color: 2.2.2]
                    parent""")).count()).isEqualTo(2);
        }

        @Test
        @DisplayName("and says when it holds nothing")
        void itSaysWhenEmpty() {
            assertThat(PaintList.of(gobFrom("make gob! [size: 0x0]")).isEmpty())
                    .isTrue();
        }

        @Test
        @DisplayName("the same tree flattens to the same list, every time")
        void itIsDeterministic() {
            // The whole arrangement rests on this. A list that varied between
            // two walks of one tree could not hold two renderers to the same
            // picture, however faithfully each executed what it was given.
            String source = """
                    parent: make gob! [size: 60x60 color: 1.1.1 alpha: 200]
                    child: make gob! [offset: 5x5 size: 40x40 color: 2.2.2]
                    append child make gob! [offset: 3x3 size: 10x10 color: 3.3.3]
                    append parent child
                    parent""";
            GobValue tree = gobFrom(source);

            assertThat(PaintList.of(tree)).isEqualTo(PaintList.of(tree));
        }

        @Test
        @DisplayName("and a surface bigger than the root does not widen what it may paint")
        void alargerSurfaceDoesNotWidenTheRoot() {
            assertThat(PaintList.onASurface(
                    gobFrom("make gob! [size: 20x20 color: 1.1.1]"), 500, 500)
                    .instructions().getFirst().where().clip())
                    .isEqualTo(new ClipRectangle(0, 0, 20, 20));
        }

        @Test
        @DisplayName("while a surface smaller than the root narrows it")
        void asmallerSurfaceNarrowsTheRoot() {
            assertThat(PaintList.onASurface(
                    gobFrom("make gob! [size: 200x200 color: 1.1.1]"), 50, 40)
                    .instructions().getFirst().where().clip())
                    .isEqualTo(new ClipRectangle(0, 0, 50, 40));
        }
    }

    @Nested
    @DisplayName("the pieces the list is built from")
    class ThePieces {

        @Test
        @DisplayName("two clips overlap to the part inside both")
        void clipsOverlap() {
            assertThat(new ClipRectangle(0, 0, 20, 20)
                    .overlapWith(new ClipRectangle(10, 10, 20, 20)))
                    .isEqualTo(new ClipRectangle(10, 10, 10, 10));
        }

        @Test
        @DisplayName("and two that miss each other overlap to nothing")
        void clipsThatMissComeToNothing() {
            assertThat(new ClipRectangle(0, 0, 10, 10)
                    .overlapWith(new ClipRectangle(50, 50, 10, 10)).isEmpty())
                    .isTrue();
        }

        @Test
        @DisplayName("an opacity outside nought to 255 is brought back inside it")
        void opacityIsClamped() {
            assertThat(new Placement(0, 0, 1, 1, ClipRectangle.nothing(), 900)
                    .opacity()).isEqualTo(255);
            assertThat(new Placement(0, 0, 1, 1, ClipRectangle.nothing(), -5)
                    .opacity()).isZero();
        }

        @Test
        @DisplayName("a colour says what a browser needs to hear")
        void acolourHasAHexTriplet() {
            assertThat(new Colour(200, 100, 50).asHexTriplet()).isEqualTo("#c86432");
            assertThat(new Colour(0, 0, 0).asHexTriplet()).isEqualTo("#000000");
        }

        @Test
        @DisplayName("and every paint kind has a word it travels under")
        void everyKindHasAWord() {
            assertThat(java.util.Arrays.stream(PaintKind.values())
                    .map(PaintKind::spelling))
                    .containsExactly("fill", "writing", "picture", "drawing");
        }
    }
}
