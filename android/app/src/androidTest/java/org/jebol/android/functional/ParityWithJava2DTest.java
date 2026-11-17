package org.jebol.android.functional;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.jebol.adapter.android.AndroidPixelSurface;
import org.jebol.adapter.android.AndroidSurface;
import org.jebol.android.AppSession;
import org.jebol.android.JebolView;
import org.jebol.adapter.host.ClasspathFiles;
import org.jebol.application.DefaultFont;
import org.jebol.application.Interpreter;
import org.jebol.application.MemoryFiles;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.render.ArgbPicture;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.ParityScenes;
import org.jebol.domain.render.PixelAgreement;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import android.graphics.Typeface;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ParityWithJava2DTest {

    private static final Typeface FACE = AndroidSurface.faceFrom(
            DefaultFont.readFrom(new ClasspathFiles()).readFontBytes());

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private AppSession session;
    private GobValue theRoot;
    private ObjectValue theDrawDialect;

    static List<ParityScenes.Shapes> shapes() {
        return ParityScenes.shapes();
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    @DisplayName("each shape scene agrees with Java2D's reference within decision 35's bound")
    void agreesWithJava2D(ParityScenes.Shapes scene) throws Exception {
        ArgbPicture painted = paintOnArt(scene.rebol());
        ArgbPicture reference;
        try (InputStream stored = ParityWithJava2DTest.class.getResourceAsStream(scene.referenceResource())) {
            assertThat(stored).as("the test APK carries %s", scene.referenceResource()).isNotNull();
            reference = ArgbPicture.readRgba(stored);
        }

        assertThat(painted.countColours())
                .as("a blank surface would agree with a blank reference and mean nothing")
                .isGreaterThanOrEqualTo(scene.fewestColours());
        PixelAgreement found = PixelAgreement.between(painted, reference, scene.insideAShapeAllowance());
        assertThat(found.insideAShape())
                .as("a pixel away from any edge that differs by more than %d is Android's Canvas "
                        + "executing an instruction differently from Java2D", scene.insideAShapeAllowance())
                .isZero();
        assertThat(found.edgeShare()).isLessThanOrEqualTo(PixelAgreement.MOST_EDGE_PIXELS_THAT_MAY_DIFFER);
    }

    @Test
    @DisplayName("a centred label's ink is in the middle of its gob")
    void centresACentredLabel() {
        ArgbPicture shown = paintOnArt(ParityScenes.A_CENTRED_LABEL);
        int left = Integer.MAX_VALUE;
        int right = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int down = 0; down < shown.height(); down++) {
            for (int across = 0; across < shown.width(); across++) {
                if (isInk(shown.rgbAt(across, down))) {
                    left = Math.min(left, across);
                    right = Math.max(right, across);
                    top = Math.min(top, down);
                    bottom = Math.max(bottom, down);
                }
            }
        }

        assertThat(right).as("some ink was written").isGreaterThanOrEqualTo(0);
        assertThat((left + right) / 2.0).as("across, the gob's middle being 20 + 150").isCloseTo(170.0, within(2.0));
        assertThat((top + bottom) / 2.0).as("down, the gob's middle being 10 + 100").isCloseTo(110.0, within(4.0));
    }

    @Test
    @DisplayName("a label too long for its gob wraps inside it, onto more than one line")
    void wrapsALongLabel() {
        ArgbPicture shown = paintOnArt(ParityScenes.A_LABEL_TOO_LONG_FOR_ITS_GOB);
        int right = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int down = 0; down < shown.height(); down++) {
            for (int across = 0; across < shown.width(); across++) {
                if (isInk(shown.rgbAt(across, down))) {
                    right = Math.max(right, across);
                    top = Math.min(top, down);
                    bottom = Math.max(bottom, down);
                }
            }
        }

        assertThat(right).as("the ink stays inside the gob, whose right edge is 20 + 300").isBetween(0, 320);
        assertThat(bottom - top).as("the ink runs over more than one 20-point line").isGreaterThan(30);
    }

    @Test
    @DisplayName("a white label's shadow is the only ink on white")
    void writesAShadow() {
        ArgbPicture shown = paintOnArt(ParityScenes.A_WHITE_LABEL_WITH_A_SHADOW);
        int inked = 0;
        for (int down = 0; down < 60; down++) {
            for (int across = 0; across < 200; across++) {
                if (isInk(shown.rgbAt(across, down))) {
                    inked++;
                }
            }
        }

        assertThat(inked).isGreaterThan(50);
    }

    private ArgbPicture paintOnArt(String scene) {
        AtomicReference<JebolView> made = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> made.set(new JebolView(context)));
        session = AppSession.open(made.get(), new MemoryFiles(), OutputPort.discard(), wanted -> {
        });
        JebolView view = made.get();
        view.measureViewport(
                Math.round(ParityScenes.WIDE * view.density().pixelsPerDp()),
                Math.round(ParityScenes.HIGH * view.density().pixelsPerDp()));
        Interpreter interpreter = session.interpreter();
        theDrawDialect = (ObjectValue) interpreter.run("wait 0 system/dialects/draw").value();
        interpreter.defineNewWordsIn(scene);
        theRoot = (GobValue) interpreter.run(scene).value();

        ImagePort.Pixels pixels = AndroidPixelSurface.paintOnWhite(
                PaintList.ofScreen(theRoot, ParityScenes.WIDE, ParityScenes.HIGH, theDrawDialect)
                        .calls(DefaultFont.readFrom(new ClasspathFiles()).measurement()),
                ParityScenes.WIDE, ParityScenes.HIGH, FACE);
        return asPicture(pixels);
    }

    private static ArgbPicture asPicture(ImagePort.Pixels pixels) {
        byte[] rgba = pixels.rgba();
        int[] argb = new int[pixels.wide() * pixels.high()];
        for (int at = 0; at < argb.length; at++) {
            argb[at] = (rgba[at * 4 + 3] & 0xFF) << 24 | (rgba[at * 4] & 0xFF) << 16
                    | (rgba[at * 4 + 1] & 0xFF) << 8 | rgba[at * 4 + 2] & 0xFF;
        }
        return new ArgbPicture(pixels.wide(), pixels.high(), argb);
    }

    private static boolean isInk(int rgb) {
        return (rgb >> 16 & 0xFF) + (rgb >> 8 & 0xFF) + (rgb & 0xFF) < 3 * 128;
    }
}
