package org.jebol.android.unit;

import org.jebol.adapter.android.AndroidPixelSurface;
import org.jebol.adapter.android.AndroidSurface;
import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.DefaultFont;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.render.ClipRectangle;
import org.jebol.domain.render.Colour;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.render.FillRule;
import org.jebol.domain.render.PaintState;
import org.jebol.domain.render.PathStep;
import org.jebol.domain.render.Placement;
import org.jebol.domain.render.PlacedText;
import org.jebol.domain.render.TextRun;
import org.jebol.domain.render.Transform;
import org.jebol.domain.value.ImageValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import android.graphics.Typeface;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AndroidSurfaceTest {

    private static final int WIDE = 60;
    private static final int HIGH = 60;
    private static final Colour RED = new Colour(255, 0, 0);
    private static final Colour GREEN = new Colour(0, 255, 0);
    private static final Colour BLUE = new Colour(0, 0, 255);
    private static final int[] WHITE = {255, 255, 255, 255};

    private static final Typeface FACE = AndroidSurface.faceFrom(
            DefaultFont.readFrom(new ClasspathFiles()).readFontBytes());

    @Test
    @DisplayName("a fill paints its colour inside its path and nothing outside it")
    void fillsItsColour() {
        ImagePort.Pixels painted = paint(fill(square(10, 10, 30, 30), RED, everywhere(), Transform.NONE));

        assertThat(rgbaAt(painted, 20, 20)).containsExactly(255, 0, 0, 255);
        assertThat(rgbaAt(painted, 5, 5)).containsExactly(WHITE);
    }

    @Test
    @DisplayName("a stroke is as wide as its line width, centred on its path")
    void strokesItsWidth() {
        PaintState painted = PaintState.AT_START.withStroke(Optional.of(BLUE)).withLineWidth(6);
        ImagePort.Pixels picture = paint(new DrawingCommand.Stroke(everywhere(),
                List.of(new PathStep.MoveTo(0, 20), new PathStep.LineTo(50, 20)), painted, Transform.NONE));

        assertThat(rgbaAt(picture, 25, 18)).containsExactly(0, 0, 255, 255);
        assertThat(rgbaAt(picture, 25, 22)).containsExactly(0, 0, 255, 255);
        assertThat(rgbaAt(picture, 25, 25)).containsExactly(WHITE);
    }

    @Test
    @DisplayName("a clip keeps a fill inside the clip's rectangle")
    void keepsInsideTheClip() {
        Placement clipped = new Placement(0, 0, WIDE, HIGH, new ClipRectangle(0, 0, 20, HIGH), Placement.OPAQUE);
        ImagePort.Pixels painted = paint(fill(square(0, 0, 40, 40), GREEN, clipped, Transform.NONE));

        assertThat(rgbaAt(painted, 10, 10)).containsExactly(0, 255, 0, 255);
        assertThat(rgbaAt(painted, 30, 10)).containsExactly(WHITE);
    }

    @Test
    @DisplayName("a transform moves a fill where it says")
    void followsTheTransform() {
        ImagePort.Pixels painted = paint(fill(square(0, 0, 10, 10), RED, everywhere(), Transform.moveBy(20, 20)));

        assertThat(rgbaAt(painted, 25, 25)).containsExactly(255, 0, 0, 255);
        assertThat(rgbaAt(painted, 5, 5)).containsExactly(WHITE);
    }

    @Test
    @DisplayName("half the opacity over white paints half the colour")
    void blendsItsOpacityOverWhite() {
        Placement halfSeen = new Placement(0, 0, WIDE, HIGH, ClipRectangle.coverWholeSurface(WIDE, HIGH), 128);
        ImagePort.Pixels painted = paint(fill(square(0, 0, 40, 40), Colour.BLACK, halfSeen, Transform.NONE));

        int[] seen = rgbaAt(painted, 20, 20);
        assertThat(seen[0]).isCloseTo(127, within(2));
        assertThat(seen[1]).isCloseTo(127, within(2));
        assertThat(seen[2]).isCloseTo(127, within(2));
    }

    @Test
    @DisplayName("a picture paints its own pixels, scaled to the size it is given")
    void paintsAPicturesPixels() {
        ImageValue image = ImageValue.of(2, 1);
        image.storage().setColourAt(1, 255, 0, 0);
        image.storage().setAlphaAt(1, 255);
        image.storage().setColourAt(2, 0, 0, 255);
        image.storage().setAlphaAt(2, 255);
        Placement where = new Placement(0, 0, 40, 20, ClipRectangle.coverWholeSurface(WIDE, HIGH), Placement.OPAQUE);

        ImagePort.Pixels painted = paint(new DrawingCommand.Picture(where, image, 40, 20, Transform.NONE));

        assertThat(rgbaAt(painted, 5, 10)).containsExactly(255, 0, 0, 255);
        assertThat(rgbaAt(painted, 35, 10)).containsExactly(0, 0, 255, 255);
        assertThat(rgbaAt(painted, 20, 40)).containsExactly(WHITE);
    }

    @Test
    @DisplayName("a text run's ink falls inside the box its size and baseline give it")
    void writesInsideItsBox() {
        PlacedText.PlacedPiece piece = new PlacedText.PlacedPiece(
                10, 40, new TextRun("MMM", Colour.BLACK, 20, false, false), "MMM");
        PlacedText placed = new PlacedText(List.of(new PlacedText.PlacedLine(List.of(piece))),
                List.of(), Optional.empty(), Optional.empty());

        ImagePort.Pixels painted = paint(new DrawingCommand.Text(everywhere(), placed, Transform.NONE));

        int inkAbove = 0;
        int inkInside = 0;
        int inkBelow = 0;
        for (int down = 0; down < HIGH; down++) {
            for (int across = 0; across < WIDE; across++) {
                if (isInk(rgbaAt(painted, across, down))) {
                    if (down < 20) {
                        inkAbove++;
                    } else if (down <= 41) {
                        inkInside++;
                    } else {
                        inkBelow++;
                    }
                }
            }
        }
        assertThat(inkInside).as("ink between the cap height and the baseline").isGreaterThan(50);
        assertThat(inkAbove).as("ink above a 20 point capital").isZero();
        assertThat(inkBelow).as("ink below the baseline of letters with no descender").isZero();
    }

    @Test
    @DisplayName("the pixel surface hands back red, green, blue and alpha, in that order, opaque over white")
    void handsBackRgba() {
        ImagePort.Pixels painted = paint(fill(square(0, 0, WIDE, HIGH), new Colour(10, 20, 30), everywhere(),
                Transform.NONE));

        assertThat(painted.wide()).isEqualTo(WIDE);
        assertThat(painted.high()).isEqualTo(HIGH);
        assertThat(painted.rgba()).hasSize(WIDE * HIGH * 4);
        assertThat(rgbaAt(painted, 0, 0)).containsExactly(10, 20, 30, 255);
    }

    private static ImagePort.Pixels paint(DrawingCommand call) {
        return AndroidPixelSurface.paintOnWhite(List.of(call), WIDE, HIGH, FACE);
    }

    private static DrawingCommand fill(List<PathStep> path, Colour colour, Placement where, Transform transform) {
        return new DrawingCommand.Fill(where, path, FillRule.NON_ZERO, Optional.empty(), colour, transform, false);
    }

    private static Placement everywhere() {
        return new Placement(0, 0, WIDE, HIGH, ClipRectangle.coverWholeSurface(WIDE, HIGH), Placement.OPAQUE);
    }

    private static List<PathStep> square(double left, double top, double right, double bottom) {
        return List.of(new PathStep.MoveTo(left, top), new PathStep.LineTo(right, top),
                new PathStep.LineTo(right, bottom), new PathStep.LineTo(left, bottom), new PathStep.Close());
    }

    private static int[] rgbaAt(ImagePort.Pixels painted, int across, int down) {
        int at = (down * painted.wide() + across) * 4;
        byte[] rgba = painted.rgba();
        return new int[] {rgba[at] & 0xFF, rgba[at + 1] & 0xFF, rgba[at + 2] & 0xFF, rgba[at + 3] & 0xFF};
    }

    private static boolean isInk(int[] rgba) {
        return rgba[0] + rgba[1] + rgba[2] < 3 * 128;
    }
}
