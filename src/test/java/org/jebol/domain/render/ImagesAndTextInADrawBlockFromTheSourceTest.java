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

class ImagesAndTextInADrawBlockFromTheSourceTest {

    private static List<PaintInstruction> drawingOf(String setUp, String drawBlock) {
        Interpreter interpreter = Interpreter.withBounds(
                Bounds.standard().granting(HostService.WINDOWS));
        String source = setUp + """

                shown: make gob! [size: 200x100]
                shown/draw: compose [""" + drawBlock + """
                ]
                shown""";
        interpreter.defineFreshWordsIn(source);
        GobValue gob = (GobValue) interpreter.run(source).value();
        ObjectValue dialect =
                (ObjectValue) interpreter.run("system/dialects/draw").value();
        return PaintList.of(gob, dialect).instructions();
    }

    private static final String A_SMALL_ORANGE_SQUARE =
            "held: make image! [20x20 255.100.40]";

    @Nested
    @DisplayName("IMAGE")
    class Images {

        @Test
        @DisplayName("one corner draws it at its own size")
        void onecornerDrawsItAtItsOwnSize() {
            List<PaintInstruction> painted =
                    drawingOf(A_SMALL_ORANGE_SQUARE, "image (held) 30x40");

            assertThat(painted).hasSize(1);
            PaintInstruction.Picture shown = (PaintInstruction.Picture) painted.getFirst();
            assertThat(shown.where().across()).isEqualTo(30);
            assertThat(shown.where().down()).isEqualTo(40);
            assertThat(shown.wide()).isEqualTo(20);
            assertThat(shown.high()).isEqualTo(20);
        }

        @Test
        @DisplayName("two corners stretch it into the box between them")
        void twoCornersStretchItIntoTheBox() {
            PaintInstruction.Picture shown = (PaintInstruction.Picture) drawingOf(
                    A_SMALL_ORANGE_SQUARE, "image (held) 10x10 110x60").getFirst();

            assertThat(shown.where().across()).isEqualTo(10);
            assertThat(shown.wide()).isEqualTo(100);
            assertThat(shown.high()).isEqualTo(50);
        }

        @Test
        @DisplayName("with no corner at all it goes at the top left")
        void withNoCornerItGoesAtTheTopLeft() {
            PaintInstruction.Picture shown = (PaintInstruction.Picture)
                    drawingOf(A_SMALL_ORANGE_SQUARE, "image (held)").getFirst();

            assertThat(shown.where().across()).isZero();
            assertThat(shown.where().down()).isZero();
        }

        @Test
        @DisplayName("it carries the transform standing when it was reached")
        void itCarriesTheTransform() {
            PaintInstruction.Picture turned = (PaintInstruction.Picture) drawingOf(
                    A_SMALL_ORANGE_SQUARE, "rotate 90 image (held) 10x10").getFirst();

            assertThat(turned.transform().isNone()).isFalse();
        }

        @Test
        @DisplayName("and the clip standing when it was reached")
        void itCarriesTheClip() {
            PaintInstruction.Picture clipped = (PaintInstruction.Picture) drawingOf(
                    A_SMALL_ORANGE_SQUARE,
                    "clip 5x5 40x40 image (held) 10x10").getFirst();

            assertThat(clipped.where().clip().wide()).isEqualTo(35);
        }

        @Test
        @DisplayName("a scaled image is resized here, so both renderers blit the same pixels")
        void ascaledImageIsResizedHere() {
            PaintInstruction.Picture shown = (PaintInstruction.Picture) drawingOf(
                    A_SMALL_ORANGE_SQUARE, "image (held) 10x10 110x60").getFirst();

            assertThat(shown.pixels().size().x())
                    .as("the pixels handed over are already the size they are drawn at")
                    .isEqualTo(100);
            assertThat(shown.pixels().size().y()).isEqualTo(50);
        }

        @Test
        @DisplayName("IMAGE-FILTER says how, and NEAREST keeps the hard edges")
        void imageFilterSaysHow() {
            String twoColours = "held: make image! [2x2 255.0.0] held/2: 0.0.255";
            PaintInstruction.Picture blocky = (PaintInstruction.Picture) drawingOf(
                    twoColours,
                    "image-filter nearest resample 1 image (held) 0x0 40x40").getFirst();
            PaintInstruction.Picture smooth = (PaintInstruction.Picture) drawingOf(
                    twoColours,
                    "image-filter bilinear resample 1 image (held) 0x0 40x40").getFirst();

            assertThat(everyColourIn(blocky))
                    .as("nearest only ever repeats the colours it was given")
                    .hasSizeLessThanOrEqualTo(2);
            assertThat(everyColourIn(smooth))
                    .as("bilinear blends, so there are colours in between")
                    .hasSizeGreaterThan(2);
        }

        private static java.util.Set<String> everyColourIn(
                PaintInstruction.Picture shown) {

            java.util.Set<String> seen = new java.util.HashSet<>();
            int wide = (int) Math.round(shown.pixels().size().x());
            int high = (int) Math.round(shown.pixels().size().y());
            for (int pixel = 1; pixel <= wide * high; pixel++) {
                int[] parts = shown.pixels().pixelAt(pixel);
                seen.add(parts[0] + "," + parts[1] + "," + parts[2]);
            }
            return seen;
        }

        @Test
        @DisplayName("and anything that is not an image paints nothing")
        void anythingThatIsNotAnImagePaintsNothing() {
            assertThat(drawingOf("", "image 10x10 20x20")).isEmpty();
        }
    }

    @Nested
    @DisplayName("TEXT")
    class Text {

        @Test
        @DisplayName("writes the strings of its block at the offset it was given")
        void writesTheStringsOfItsBlock() {
            List<PaintInstruction> painted =
                    drawingOf("", "text vectorial 20x30 160x40 [{hello}]");

            assertThat(painted).hasSize(1);
            PaintInstruction.Writing written =
                    (PaintInstruction.Writing) painted.getFirst();
            assertThat(written.text()).isEqualTo("hello");
            assertThat(written.where().across()).isEqualTo(20);
            assertThat(written.where().down()).isEqualTo(30);
        }

        @Test
        @DisplayName("each string is its own run, laid out along the line")
        void eachStringIsItsOwnRun() {
            List<PaintInstruction> painted =
                    drawingOf("", "text vectorial 0x0 200x40 [{one} {two}]");

            assertThat(painted).hasSize(2);
            assertThat(((PaintInstruction.Writing) painted.get(0)).text())
                    .isEqualTo("one");
            assertThat(((PaintInstruction.Writing) painted.get(1)).text())
                    .isEqualTo("two");
            assertThat(painted.get(1).where().across())
                    .as("the second starts where the first ran out")
                    .isGreaterThan(painted.get(0).where().across());
        }

        @Test
        @DisplayName("BOLD and ITALIC before a string style it, and the slash turns it off")
        void boldAndItalicStyleWhatFollows() {
            List<PaintInstruction> painted = drawingOf("",
                    "text vectorial 0x0 200x40 [bold {loud} /bold {quiet} italic {leaning}]");

            assertThat(painted).hasSize(3);
            assertThat(((PaintInstruction.Writing) painted.get(0)).bold()).isTrue();
            assertThat(((PaintInstruction.Writing) painted.get(1)).bold()).isFalse();
            assertThat(((PaintInstruction.Writing) painted.get(2)).italic()).isTrue();
        }

        @Test
        @DisplayName("a number sets the size and a tuple sets the colour of what follows")
        void anumberSetsTheSizeAndATupleTheColour() {
            List<PaintInstruction> painted = drawingOf("",
                    "text vectorial 0x0 200x40 [24 255.0.0 {big and red}]");

            PaintInstruction.Writing written =
                    (PaintInstruction.Writing) painted.getFirst();
            assertThat(written.size()).isEqualTo(24);
            assertThat(written.colour()).isEqualTo(new Colour(255, 0, 0));
        }

        @Test
        @DisplayName("it takes its colour from the pen")
        void ittakesItsColourFromThePen() {
            PaintInstruction.Writing written = (PaintInstruction.Writing) drawingOf(
                    "", "pen 255.0.0 text vectorial 0x0 200x40 [{red}]").getFirst();

            assertThat(written.colour()).isEqualTo(new Colour(255, 0, 0));
        }

        @Test
        @DisplayName("with the pen off there is nothing to write with, so nothing is written")
        void withThePenOffNothingIsWritten() {
            assertThat(drawingOf("", "pen off text vectorial 0x0 200x40 [{none}]"))
                    .isEmpty();
        }

        @Test
        @DisplayName("and a block with no strings in it writes nothing")
        void ablockWithNoStringsWritesNothing() {
            assertThat(drawingOf("", "text vectorial 0x0 200x40 [bold italic]"))
                    .isEmpty();
            assertThat(drawingOf("", "text vectorial 0x0 200x40 []")).isEmpty();
        }
    }
}
