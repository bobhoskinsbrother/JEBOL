package org.jebol.adapter.host;

import org.jebol.application.AnInterpreter;
import org.jebol.application.Bounds;
import org.jebol.application.DefaultFont;
import org.jebol.application.Interpreter;
import org.jebol.application.OneSurfaceScreen;
import org.jebol.application.TheCarriedFace;
import org.jebol.application.TheCarriedFont;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.OutputPort;
import org.jebol.domain.host.WindowHistory;
import org.jebol.domain.render.ArgbPicture;
import org.jebol.domain.render.DrawingCommand;
import org.jebol.domain.render.PaintList;
import org.jebol.domain.render.ParityScenes;
import org.jebol.domain.render.PixelAgreement;
import org.jebol.domain.render.TextMeasurement;
import org.jebol.domain.value.GobValue;
import org.jebol.domain.value.ObjectValue;
import org.jebol.domain.value.PairValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Java2DParityReferencesFromTheSourceTest {

    private static final Path WHERE_THE_REFERENCES_ARE_KEPT = Path.of("src", "testFixtures", "resources");

    private static final boolean IS_WRITING = Boolean.getBoolean("parity.write");

    static List<ParityScenes.Shapes> shapes() {
        return ParityScenes.shapes();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    @DisplayName("Java2D paints each shape scene as its checked-in reference holds it")
    void paintsAsTheReferenceHolds(ParityScenes.Shapes scene) throws IOException {
        ArgbPicture painted = paintWithJava2D(scene);

        Path kept = WHERE_THE_REFERENCES_ARE_KEPT.resolve(scene.referenceResource().substring(1));
        if (IS_WRITING) {
            Files.createDirectories(kept.getParent());
            Files.write(kept, painted.writeRgba());
        }
        ArgbPicture reference = IS_WRITING
                ? ArgbPicture.readRgba(Files.newInputStream(kept))
                : readReference(scene);

        assertThat(reference.width()).isEqualTo(ParityScenes.WIDE);
        assertThat(reference.height()).isEqualTo(ParityScenes.HIGH);
        assertThat(reference.countColours())
                .as("a blank reference would agree with a blank device and mean nothing")
                .isGreaterThanOrEqualTo(scene.fewestColours());
        PixelAgreement found = PixelAgreement.between(painted, reference, PixelAgreement.EXACTLY);
        assertThat(found.insideAShape())
                .as("Java2D today paints %s as the reference holds it; rewrite the references with "
                        + "-Dparity.write=true only when the change is meant", scene)
                .isZero();
        assertThat(found.edgeShare()).isLessThanOrEqualTo(PixelAgreement.MOST_EDGE_PIXELS_THAT_MAY_DIFFER);
    }

    private static ArgbPicture readReference(ParityScenes.Shapes scene) throws IOException {
        try (InputStream stored = Java2DParityReferencesFromTheSourceTest.class
                .getResourceAsStream(scene.referenceResource())) {
            assertThat(stored)
                    .as("%s has no reference; ./gradlew :desktop:functionalTests -Dparity.write=true paints them",
                            scene.referenceResource())
                    .isNotNull();
            return ArgbPicture.readRgba(stored);
        }
    }

    private static ArgbPicture paintWithJava2D(ParityScenes.Shapes scene) {
        OneSurfaceScreen screen = OneSurfaceScreen.showTo(
                new AViewerThatOnlyLooks(), new WindowHistory(), new ClasspathFiles());
        Interpreter interpreter = Interpreter.createOn(
                AnInterpreter.standardPorts().withScreen(screen),
                OutputPort.discard(),
                Bounds.standard().grant(HostService.WINDOWS).withTimeLimit(Duration.ofSeconds(20)));
        screen.measure(ParityScenes.WIDE, ParityScenes.HIGH);
        interpreter.run("wait 0");
        ObjectValue drawDialect = (ObjectValue) interpreter.run("system/dialects/draw").value();
        interpreter.defineNewWordsIn(scene.rebol());
        GobValue root = (GobValue) interpreter.run(scene.rebol()).value();
        assertThat(root.storage().size())
                .as("the screen was measured before the scene opened")
                .isEqualTo(PairValue.of(ParityScenes.WIDE, ParityScenes.HIGH));

        BufferedImage surface = new BufferedImage(ParityScenes.WIDE, ParityScenes.HIGH, BufferedImage.TYPE_INT_RGB);
        Graphics2D onto = surface.createGraphics();
        try {
            onto.setColor(Color.WHITE);
            onto.fillRect(0, 0, ParityScenes.WIDE, ParityScenes.HIGH);
            Java2DSurface.execute(onto,
                    PaintList.ofScreen(root, ParityScenes.WIDE, ParityScenes.HIGH, drawDialect),
                    TheCarriedFont.measurement(), TheCarriedFace.face());
        } finally {
            onto.dispose();
        }
        return ArgbPicture.opaque(ParityScenes.WIDE, ParityScenes.HIGH,
                surface.getRGB(0, 0, ParityScenes.WIDE, ParityScenes.HIGH, null, 0, ParityScenes.WIDE));
    }

    private static final class AViewerThatOnlyLooks implements OneSurfaceScreen.Viewer {

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public TextMeasurement textMeasurement(DefaultFont carried) {
            return carried.measurement();
        }

        @Override
        public void paint(List<DrawingCommand> painting) {
        }
    }
}
